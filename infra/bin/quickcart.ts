#!/usr/bin/env node
import 'source-map-support/register';
import * as cdk from 'aws-cdk-lib';
import { NetworkStack } from '../lib/network-stack';
import { DataStack } from '../lib/data-stack';
import { ServicesStack } from '../lib/services-stack';

const app = new cdk.App();

const env = {
  account: '132977459165',
  region:  'eu-west-2',
};

// Three stacks with explicit dependency ordering:
//   NetworkStack → DataStack → ServicesStack
//
// Why three stacks instead of one?
// Each stack has its own CloudFormation change set and deployment lifecycle.
// The VPC/subnets almost never change; the databases change rarely; the ECS
// services change on every deploy. Splitting them means a new Docker image
// deploy touches only ServicesStack, not the 40-resource network layer —
// faster deploys, smaller blast radius, and independent rollback targets.

const network = new NetworkStack(app, 'QuickCart-Network', { env });

const data = new DataStack(app, 'QuickCart-Data', {
  env,
  vpc: network.vpc,
  dbSecurityGroup: network.dbSecurityGroup,
  cacheSecurityGroup: network.cacheSecurityGroup,
  mqSecurityGroup: network.mqSecurityGroup,
});
data.addDependency(network);

const services = new ServicesStack(app, 'QuickCart-Services', {
  env,
  vpc: network.vpc,
  albSecurityGroup: network.albSecurityGroup,
  serviceSecurityGroup: network.serviceSecurityGroup,
  orderDb: data.orderDb,
  inventoryDb: data.inventoryDb,
  mqBroker: data.mqBroker,
  redisCluster: data.redisCluster,
  mqSecret: data.mqSecret,
});
services.addDependency(data);
