import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import { Construct } from 'constructs';

/**
 * Provisions the VPC and all security groups.
 *
 * VPC layout (2 AZs for HA, enough for a learning/interview project):
 *   - Public subnets:  NAT Gateways, ALB
 *   - Private subnets: ECS Fargate tasks (outbound via NAT, no inbound)
 *   - Isolated subnets: RDS, ElastiCache, Amazon MQ (no internet at all)
 *
 * Security group rules follow least-privilege:
 *   - ECS tasks can reach DB/cache/MQ on their respective ports only
 *   - DB/cache/MQ only accept traffic from ECS tasks, never the internet
 *   - No security group allows 0.0.0.0/0 ingress on data ports
 */
export class NetworkStack extends cdk.Stack {

  readonly vpc: ec2.Vpc;
  readonly albSecurityGroup: ec2.SecurityGroup;
  readonly serviceSecurityGroup: ec2.SecurityGroup;
  readonly dbSecurityGroup: ec2.SecurityGroup;
  readonly cacheSecurityGroup: ec2.SecurityGroup;
  readonly mqSecurityGroup: ec2.SecurityGroup;

  constructor(scope: Construct, id: string, props: cdk.StackProps) {
    super(scope, id, props);

    this.vpc = new ec2.Vpc(this, 'Vpc', {
      maxAzs: 2,
      natGateways: 1, // 1 NAT GW is enough for dev/learning; prod would use 1 per AZ
      subnetConfiguration: [
        { name: 'Public',   subnetType: ec2.SubnetType.PUBLIC,              cidrMask: 24 },
        { name: 'Private',  subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS, cidrMask: 24 },
        { name: 'Isolated', subnetType: ec2.SubnetType.PRIVATE_ISOLATED,    cidrMask: 24 },
      ],
    });

    // Application Load Balancer (defined here so ALB SG rules don't
    // create a cross-stack reference cycle back into ServicesStack)
    this.albSecurityGroup = new ec2.SecurityGroup(this, 'AlbSg', {
      vpc: this.vpc,
      description: 'Public ALB - accepts HTTP from the internet',
      allowAllOutbound: true,
    });
    this.albSecurityGroup.addIngressRule(ec2.Peer.anyIpv4(), ec2.Port.tcp(80), 'HTTP from internet');

    // ECS Fargate tasks
    this.serviceSecurityGroup = new ec2.SecurityGroup(this, 'ServiceSg', {
      vpc: this.vpc,
      description: 'ECS Fargate tasks - order-service and inventory-service',
      allowAllOutbound: true,
    });

    // RDS Postgres
    this.dbSecurityGroup = new ec2.SecurityGroup(this, 'DbSg', {
      vpc: this.vpc,
      description: 'RDS Postgres - only reachable from ECS tasks',
      allowAllOutbound: false,
    });
    this.dbSecurityGroup.addIngressRule(
      this.serviceSecurityGroup,
      ec2.Port.tcp(5432),
      'ECS tasks to Postgres',
    );

    // Allow ALB to reach ECS tasks on service ports
    this.serviceSecurityGroup.addIngressRule(this.albSecurityGroup, ec2.Port.tcp(8081), 'ALB to order-service');
    this.serviceSecurityGroup.addIngressRule(this.albSecurityGroup, ec2.Port.tcp(8082), 'ALB to inventory-service');

    // ElastiCache Redis
    this.cacheSecurityGroup = new ec2.SecurityGroup(this, 'CacheSg', {
      vpc: this.vpc,
      description: 'ElastiCache Redis - only reachable from ECS tasks',
      allowAllOutbound: false,
    });
    this.cacheSecurityGroup.addIngressRule(
      this.serviceSecurityGroup,
      ec2.Port.tcp(6379),
      'ECS tasks to Redis',
    );

    // Amazon MQ (RabbitMQ)
    this.mqSecurityGroup = new ec2.SecurityGroup(this, 'MqSg', {
      vpc: this.vpc,
      description: 'Amazon MQ RabbitMQ - only reachable from ECS tasks',
      allowAllOutbound: false,
    });
    this.mqSecurityGroup.addIngressRule(
      this.serviceSecurityGroup,
      ec2.Port.tcp(5671), // AMQP over TLS
      'ECS tasks to RabbitMQ',
    );
  }
}
