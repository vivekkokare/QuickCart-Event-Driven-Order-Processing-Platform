import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as ecr from 'aws-cdk-lib/aws-ecr';
import * as elbv2 from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import * as rds from 'aws-cdk-lib/aws-rds';
import * as elasticache from 'aws-cdk-lib/aws-elasticache';
import * as amazonmq from 'aws-cdk-lib/aws-amazonmq';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
import * as logs from 'aws-cdk-lib/aws-logs';
import { Construct } from 'constructs';

interface ServicesStackProps extends cdk.StackProps {
  vpc: ec2.Vpc;
  albSecurityGroup: ec2.SecurityGroup;
  serviceSecurityGroup: ec2.SecurityGroup;
  orderDb: rds.DatabaseInstance;
  inventoryDb: rds.DatabaseInstance;
  mqBroker: amazonmq.CfnBroker;
  redisCluster: elasticache.CfnReplicationGroup;
  mqSecret: secretsmanager.Secret;
}

/**
 * Provisions ECR repositories, ECS cluster, and Fargate services.
 *
 * Each microservice gets:
 *   - An ECR repository for its Docker images
 *   - An ECS task definition (CPU/memory, env vars, secrets, log group)
 *   - An ECS Fargate service (desired count, health check)
 *
 * order-service also gets an Application Load Balancer so it can receive
 * HTTP traffic from the internet. inventory-service is internal-only
 * (it only listens to RabbitMQ, not HTTP from the public).
 *
 * Secrets Manager integration: sensitive values (DB password, MQ password)
 * are referenced by ARN in the task definition. ECS fetches them at
 * container startup and injects them as environment variables. The
 * application code reads them from env vars — same as local development,
 * no code change needed.
 */
export class ServicesStack extends cdk.Stack {

  constructor(scope: Construct, id: string, props: ServicesStackProps) {
    super(scope, id, props);

    // ── ECR Repositories ───────────────────────────────────────────────────
    const orderServiceRepo = new ecr.Repository(this, 'OrderServiceRepo', {
      repositoryName: 'quickcart/order-service',
      removalPolicy: cdk.RemovalPolicy.DESTROY,
      emptyOnDelete: true,
      lifecycleRules: [{
        maxImageCount: 10, // keep last 10 images; older ones are auto-deleted
        description: 'Keep last 10 images',
      }],
    });

    const inventoryServiceRepo = new ecr.Repository(this, 'InventoryServiceRepo', {
      repositoryName: 'quickcart/inventory-service',
      removalPolicy: cdk.RemovalPolicy.DESTROY,
      emptyOnDelete: true,
      lifecycleRules: [{ maxImageCount: 10, description: 'Keep last 10 images' }],
    });

    // ── ECS Cluster ────────────────────────────────────────────────────────
    const cluster = new ecs.Cluster(this, 'Cluster', {
      vpc: props.vpc,
      clusterName: 'quickcart',
    });

    // ── Shared config helpers ──────────────────────────────────────────────
    // Amazon MQ returns AmqpEndpoints as a list like ["amqps://host:5671"].
    // We split on "//" and take index 1 to get "host:5671", then split on
    // ":" to get the hostname. Fn.select operates on CloudFormation tokens
    // and resolves at deploy time, not synthesis time.
    const mqEndpoint = cdk.Fn.select(0, props.mqBroker.attrAmqpEndpoints);
    const mqHost = cdk.Fn.select(0, cdk.Fn.split(':', cdk.Fn.select(1, cdk.Fn.split('//', mqEndpoint))));

    const redisHost = props.redisCluster.attrPrimaryEndPointAddress;

    // Each DB instance auto-generated its own secret via fromGeneratedSecret.
    // We access them via instance.secret (never null at this point — CDK
    // guarantees the secret exists when credentials are generated).
    const orderDbSecret     = ecs.Secret.fromSecretsManager(props.orderDb.secret!, 'password');
    const inventoryDbSecret = ecs.Secret.fromSecretsManager(props.inventoryDb.secret!, 'password');
    const mqPasswordSecret  = ecs.Secret.fromSecretsManager(props.mqSecret, 'password');

    // ── order-service ──────────────────────────────────────────────────────
    const orderLogGroup = new logs.LogGroup(this, 'OrderServiceLogs', {
      logGroupName: '/quickcart/order-service',
      retention: logs.RetentionDays.ONE_WEEK,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    });

    const orderTaskDef = new ecs.FargateTaskDefinition(this, 'OrderTaskDef', {
      cpu: 512,
      memoryLimitMiB: 1024,
    });
    props.orderDb.secret!.grantRead(orderTaskDef.taskRole);
    props.mqSecret.grantRead(orderTaskDef.taskRole);

    orderTaskDef.addContainer('order-service', {
      image: ecs.ContainerImage.fromEcrRepository(orderServiceRepo, 'latest'),
      portMappings: [{ containerPort: 8081 }],
      logging: ecs.LogDrivers.awsLogs({
        logGroup: orderLogGroup,
        streamPrefix: 'order-service',
      }),
      environment: {
        SPRING_DATASOURCE_URL:            `jdbc:postgresql://${props.orderDb.dbInstanceEndpointAddress}:5432/order_system`,
        SPRING_DATASOURCE_USERNAME:       'order_admin',
        SPRING_RABBITMQ_HOST:             mqHost,
        SPRING_RABBITMQ_PORT:             '5671',
        SPRING_RABBITMQ_USERNAME:         'order_admin',
        APP_INVENTORY_SERVICE_BASE_URL:   'http://inventory-service.quickcart.internal:8082',
      },
      secrets: {
        SPRING_DATASOURCE_PASSWORD: orderDbSecret,
        SPRING_RABBITMQ_PASSWORD:   mqPasswordSecret,
      },
      healthCheck: {
        command: ['CMD-SHELL', 'curl -f http://localhost:8081/actuator/health || exit 1'],
        interval: cdk.Duration.seconds(30),
        timeout: cdk.Duration.seconds(5),
        retries: 3,
      },
    });

    // Public ALB for order-service (the only service that accepts internet traffic)
    const alb = new elbv2.ApplicationLoadBalancer(this, 'Alb', {
      vpc: props.vpc,
      internetFacing: true,
      vpcSubnets: { subnetType: ec2.SubnetType.PUBLIC },
      securityGroup: props.albSecurityGroup,
    });

    const albListener = alb.addListener('HttpListener', { port: 80, open: false });

    const orderService = new ecs.FargateService(this, 'OrderService', {
      cluster,
      taskDefinition: orderTaskDef,
      desiredCount: 0,
      securityGroups: [props.serviceSecurityGroup],
      vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS },
      assignPublicIp: false,
      serviceName: 'order-service',
      // Roll back automatically if new tasks fail health checks — avoids
      // a broken deploy blocking all future deploys for up to 3 hours.
      circuitBreaker: { rollback: true },
      // 100% ensures at least 1 task is always running during a rolling
      // update. With desiredCount=1, the default 50% would mean 0 tasks
      // running while the new task starts — brief outage on every deploy.
      minHealthyPercent: 100,
    });

    albListener.addTargets('OrderServiceTarget', {
      port: 8081,
      protocol: elbv2.ApplicationProtocol.HTTP,
      targets: [orderService],
      healthCheck: {
        path: '/actuator/health',
        interval: cdk.Duration.seconds(30),
      },
    });

    // SG rules (ALB → service) are defined in NetworkStack to avoid
    // cross-stack reference cycles.

    // ── inventory-service ──────────────────────────────────────────────────
    const inventoryLogGroup = new logs.LogGroup(this, 'InventoryServiceLogs', {
      logGroupName: '/quickcart/inventory-service',
      retention: logs.RetentionDays.ONE_WEEK,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    });

    const inventoryTaskDef = new ecs.FargateTaskDefinition(this, 'InventoryTaskDef', {
      cpu: 512,
      memoryLimitMiB: 1024,
    });
    props.inventoryDb.secret!.grantRead(inventoryTaskDef.taskRole);
    props.mqSecret.grantRead(inventoryTaskDef.taskRole);

    inventoryTaskDef.addContainer('inventory-service', {
      image: ecs.ContainerImage.fromEcrRepository(inventoryServiceRepo, 'latest'),
      portMappings: [{ containerPort: 8082 }],
      logging: ecs.LogDrivers.awsLogs({
        logGroup: inventoryLogGroup,
        streamPrefix: 'inventory-service',
      }),
      environment: {
        SPRING_DATASOURCE_URL:      `jdbc:postgresql://${props.inventoryDb.dbInstanceEndpointAddress}:5432/inventory_system`,
        SPRING_DATASOURCE_USERNAME: 'order_admin',
        SPRING_RABBITMQ_HOST:       mqHost,
        SPRING_RABBITMQ_PORT:       '5671',
        SPRING_RABBITMQ_USERNAME:   'order_admin',
        SPRING_DATA_REDIS_HOST:     redisHost,
        SPRING_DATA_REDIS_PORT:     '6379',
      },
      secrets: {
        SPRING_DATASOURCE_PASSWORD: inventoryDbSecret,
        SPRING_RABBITMQ_PASSWORD:   mqPasswordSecret,
      },
      healthCheck: {
        command: ['CMD-SHELL', 'curl -f http://localhost:8082/actuator/health || exit 1'],
        interval: cdk.Duration.seconds(30),
        timeout: cdk.Duration.seconds(5),
        retries: 3,
      },
    });

    new ecs.FargateService(this, 'InventoryService', {
      cluster,
      taskDefinition: inventoryTaskDef,
      desiredCount: 0,
      securityGroups: [props.serviceSecurityGroup],
      vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS },
      assignPublicIp: false,
      serviceName: 'inventory-service',
      circuitBreaker: { rollback: true },
      minHealthyPercent: 100,
    });

    // ── Outputs ────────────────────────────────────────────────────────────
    new cdk.CfnOutput(this, 'OrderServiceUrl', {
      value: `http://${alb.loadBalancerDnsName}`,
      description: 'Public URL for the order-service API',
    });
    new cdk.CfnOutput(this, 'OrderServiceEcrUri', {
      value: orderServiceRepo.repositoryUri,
      description: 'ECR URI for order-service — use in CI docker push',
    });
    new cdk.CfnOutput(this, 'InventoryServiceEcrUri', {
      value: inventoryServiceRepo.repositoryUri,
      description: 'ECR URI for inventory-service — use in CI docker push',
    });
  }
}
