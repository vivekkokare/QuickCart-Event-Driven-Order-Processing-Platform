import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as rds from 'aws-cdk-lib/aws-rds';
import * as elasticache from 'aws-cdk-lib/aws-elasticache';
import * as amazonmq from 'aws-cdk-lib/aws-amazonmq';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
import { Construct } from 'constructs';

interface DataStackProps extends cdk.StackProps {
  vpc: ec2.Vpc;
  dbSecurityGroup: ec2.SecurityGroup;
  cacheSecurityGroup: ec2.SecurityGroup;
  mqSecurityGroup: ec2.SecurityGroup;
}

/**
 * Provisions all stateful infrastructure: RDS, ElastiCache, Amazon MQ.
 *
 * Each RDS instance generates its own Secrets Manager secret via
 * Credentials.fromGeneratedSecret — this is the CDK-idiomatic approach.
 * CDK auto-attaches the secret to the instance and the secret is
 * accessible via instance.secret after construction. Each service gets
 * its own credential so a compromise of one doesn't expose the other.
 */
export class DataStack extends cdk.Stack {

  readonly orderDb: rds.DatabaseInstance;
  readonly inventoryDb: rds.DatabaseInstance;
  readonly mqBroker: amazonmq.CfnBroker;
  readonly redisCluster: elasticache.CfnReplicationGroup;
  readonly mqSecret: secretsmanager.Secret;

  constructor(scope: Construct, id: string, props: DataStackProps) {
    super(scope, id, props);

    const isolatedSubnets = { subnetType: ec2.SubnetType.PRIVATE_ISOLATED };

    // ── MQ Secret ──────────────────────────────────────────────────────────
    this.mqSecret = new secretsmanager.Secret(this, 'MqSecret', {
      secretName: 'quickcart/mq-credentials',
      generateSecretString: {
        secretStringTemplate: JSON.stringify({ username: 'order_admin' }),
        generateStringKey: 'password',
        excludePunctuation: true,
        passwordLength: 32,
      },
    });

    // ── RDS Postgres ────────────────────────────────────────────────────────
    // Credentials.fromGeneratedSecret creates a Secrets Manager secret per
    // instance automatically — no "already attached" conflict.
    const dbSubnetGroup = new rds.SubnetGroup(this, 'DbSubnetGroup', {
      description: 'Isolated subnets for RDS instances',
      vpc: props.vpc,
      vpcSubnets: isolatedSubnets,
    });

    const commonDbProps = {
      engine: rds.DatabaseInstanceEngine.postgres({ version: rds.PostgresEngineVersion.VER_16 }),
      instanceType: ec2.InstanceType.of(ec2.InstanceClass.T3, ec2.InstanceSize.MICRO),
      vpc: props.vpc,
      securityGroups: [props.dbSecurityGroup],
      subnetGroup: dbSubnetGroup,
      multiAz: false,
      storageEncrypted: true,
      deletionProtection: false,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    };

    this.orderDb = new rds.DatabaseInstance(this, 'OrderDb', {
      ...commonDbProps,
      databaseName: 'order_system',
      credentials: rds.Credentials.fromGeneratedSecret('order_admin', {
        secretName: 'quickcart/order-db-credentials',
      }),
    });

    this.inventoryDb = new rds.DatabaseInstance(this, 'InventoryDb', {
      ...commonDbProps,
      databaseName: 'inventory_system',
      credentials: rds.Credentials.fromGeneratedSecret('order_admin', {
        secretName: 'quickcart/inventory-db-credentials',
      }),
    });

    // ── Amazon MQ (RabbitMQ) ───────────────────────────────────────────────
    const mqSubnets = props.vpc.selectSubnets(isolatedSubnets);

    this.mqBroker = new amazonmq.CfnBroker(this, 'MqBroker', {
      brokerName: 'quickcart-rabbitmq',
      engineType: 'RABBITMQ',
      engineVersion: '3.13',
      hostInstanceType: 'mq.m5.large',
      deploymentMode: 'SINGLE_INSTANCE',
      publiclyAccessible: false,
      subnetIds: [mqSubnets.subnetIds[0]],
      securityGroups: [props.mqSecurityGroup.securityGroupId],
      users: [{
        username: 'order_admin',
        password: this.mqSecret.secretValueFromJson('password').unsafeUnwrap(),
      }],
    });

    // ── ElastiCache Redis ──────────────────────────────────────────────────
    const cacheSubnets = props.vpc.selectSubnets(isolatedSubnets);
    const cacheSubnetGroup = new elasticache.CfnSubnetGroup(this, 'CacheSubnetGroup', {
      description: 'Isolated subnets for ElastiCache Redis',
      subnetIds: cacheSubnets.subnetIds,
    });

    this.redisCluster = new elasticache.CfnReplicationGroup(this, 'Redis', {
      replicationGroupDescription: 'QuickCart stock cache',
      numCacheClusters: 1,
      cacheNodeType: 'cache.t3.micro',
      engine: 'redis',
      engineVersion: '7.0',
      cacheSubnetGroupName: cacheSubnetGroup.ref,
      securityGroupIds: [props.cacheSecurityGroup.securityGroupId],
      atRestEncryptionEnabled: true,
      transitEncryptionEnabled: false,
      automaticFailoverEnabled: false,
      multiAzEnabled: false,
    });
    this.redisCluster.addDependency(cacheSubnetGroup);
  }
}
