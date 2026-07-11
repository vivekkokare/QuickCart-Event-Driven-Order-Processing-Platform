# ADR-007: AWS Infrastructure Design (ECS Fargate, RDS, Amazon MQ, ElastiCache)

**Status:** Accepted  
**Date:** 2026-07

## Context

The system needs a production-grade cloud deployment. Key infrastructure decisions: compute platform, database, broker, cache, and network topology.

## Decisions

### Compute: ECS Fargate (not EC2, not Lambda, not EKS)

**Fargate** was chosen over EC2 Auto Scaling because there are no EC2 instances to manage — no patching, no AMI selection, no capacity reservation. The container scheduler handles placement.

**Fargate over Lambda** because these are long-running Spring Boot services with persistent connections (DB, RabbitMQ). Lambda's cold start and stateless model is a poor fit for connection-pool-heavy applications. Lambda shines for event-driven, short-duration functions.

**Fargate over EKS** because Kubernetes adds significant operational complexity (control plane, node groups, RBAC, ingress controllers) that is not proportionate for two microservices. EKS is the right choice when you need Kubernetes-specific features: custom schedulers, Helm charts, complex pod scheduling, etc.

### Database: RDS Postgres (one instance per service)

Each service gets its own RDS instance. Shared databases between microservices violate service autonomy — a schema change in inventory can break order queries. The extra cost is justified by independent schema evolution.

Postgres over MySQL: better JSON support, stronger ACID guarantees, `FOR UPDATE SKIP LOCKED` for reliable outbox polling.

### Message Broker: Amazon MQ (RabbitMQ)

Amazon MQ is the managed RabbitMQ service. We use RabbitMQ because the application code already uses Spring AMQP (`spring-boot-starter-amqp`). Amazon MQ means no broker cluster to manage.

**Why not Amazon SQS/SNS?** SQS would require rewriting all the RabbitMQ exchange/queue/binding configuration. SQS also lacks RabbitMQ's DLQ routing flexibility (dead-letter exchanges with per-queue configuration).

**Why not Amazon MSK (Kafka)?** Kafka is the right choice for event streaming at very high throughput with long retention. For order processing at Blinkit scale (millions of orders/day), Kafka becomes relevant. For this project, RabbitMQ's simpler mental model (exchanges, bindings, queues) is appropriate.

### Cache: ElastiCache Redis

Managed Redis with no replication (single node, `numCacheClusters: 1`) — appropriate for a development/demo system. Production would use a replication group (1 primary + 1 read replica) for HA.

Redis over Memcached: Redis supports richer data structures, persistence, and pub/sub — useful for future features (rate limiting, session storage, leaderboards).

### Network Topology: Three-Tier VPC

```
Public subnets:    ALB, NAT Gateway
Private subnets:   ECS Fargate tasks (outbound via NAT, no inbound)
Isolated subnets:  RDS, ElastiCache, Amazon MQ (no internet at all)
```

**Why isolated subnets for data stores?** Data stores in private subnets (with NAT) could theoretically initiate outbound connections to the internet. Isolated subnets have no route table entry for the internet gateway or NAT — they are physically incapable of internet communication. This is defence in depth.

**Why ALB over NLB?** Application Load Balancer understands HTTP — host-based routing, path-based routing, health check response parsing, sticky sessions. We use the HTTP layer. NLB operates at TCP level (Layer 4) — right for protocols other than HTTP or when you need static IPs.

### Infrastructure-as-Code: AWS CDK (TypeScript)

CDK over CloudFormation YAML because CloudFormation templates for this system would be ~3000 lines of YAML with no type safety. CDK gives TypeScript types, IDE autocomplete, and constructs (VPC, ECS cluster, RDS instance) that encapsulate CloudFormation boilerplate.

CDK over Terraform because we're already in the AWS ecosystem. CDK has native first-class support for AWS resources and generates CloudFormation under the hood — the gold standard for AWS deployment.

## Three-Stack Architecture

```
QuickCart-Network → QuickCart-Data → QuickCart-Services
```

Separated because each stack has a different change frequency:
- Network: changes never (VPC/SGs are stable infrastructure)
- Data: changes rarely (schema migrations, engine upgrades)
- Services: changes on every deploy (new Docker image tag)

A broken services deploy can be rolled back without touching the VPC or databases.
