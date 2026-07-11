# QuickCart — Event-Driven Order Processing Platform

A microservices-based order processing backend modelled on quick-commerce platforms like Blinkit. Built with Java 17, Spring Boot 3, PostgreSQL, RabbitMQ, and Redis — deployed to AWS with ECS Fargate, CDK, and a GitHub Actions CI/CD pipeline.

## Services

| Service | Port | Responsibility |
|---|---|---|
| `order-service` | 8081 | Accepts orders, validates stock, publishes `OrderCreated` events |
| `inventory-service` | 8082 | Consumes events, manages stock levels, exposes stock query API |

## Architecture

Both services follow Clean Architecture (domain → application → infrastructure → api). The domain layer has no framework dependencies — it can be tested without Spring, JPA, or a message broker.

Inter-service communication is split into two channels:

- **Synchronous (HTTP):** order-service calls inventory-service for a pre-flight stock check before accepting an order. This path is protected by a Resilience4j circuit breaker with an optimistic fallback — if inventory-service is unreachable, the order is accepted and the definitive check happens downstream.

- **Asynchronous (RabbitMQ):** after persisting an order, order-service writes an `OrderCreated` event to an outbox table in the same database transaction. A poller publishes it to RabbitMQ. inventory-service consumes it, checks real stock levels, and deducts.

### Reliability

**Transactional Outbox** — the order row and the outbox event are written in one ACID transaction. The broker write is decoupled and retried independently, so there is no window where an order exists without an event being eventually published.

**Idempotent Consumer** — inventory-service records processed event IDs in a `processed_events` table. Redelivered messages (broker restart, consumer crash) are silently discarded. The idempotency check and the stock deduction happen in the same transaction.

**Dead Letter Queue** — messages that fail processing after retries are routed to a DLQ for inspection and replay.

### Caching

inventory-service caches stock by `productId` in Redis using a decorator over the JPA repository (`CachingStockRepository` wraps `StockRepositoryAdapter`). The domain model carries no Jackson or cache annotations. TTL is 5 minutes; the cache is evicted on every stock write.

## Tech Stack

- **Java 17**, **Spring Boot 3.5**, **Spring Data JPA**, **Spring AMQP**
- **PostgreSQL 16** — one database per service
- **RabbitMQ** — topic exchange, DLX/DLQ per queue
- **Redis** — stock cache
- **Resilience4j** — circuit breaker on the inventory HTTP call
- **Docker** — multi-stage builds (JDK builder → JRE runtime, non-root user)
- **AWS** — ECS Fargate, RDS, Amazon MQ, ElastiCache, ALB, Secrets Manager
- **AWS CDK (TypeScript)** — three-stack infrastructure (Network → Data → Services)
- **GitHub Actions** — CI (test matrix across both services) + CD (build, push to ECR, deploy to ECS)

## Running Locally

Start the shared infrastructure:

```bash
docker compose up -d
```

| Service | Port | Notes |
|---|---|---|
| PostgreSQL | 5432 | `order_admin` / databases: `order_system`, `inventory_system` |
| RabbitMQ | 5672, 15672 | Management UI: http://localhost:15672 (guest/guest) |
| Redis | 6379 | No auth |

Run each service from its directory:

```bash
cd order-service && ./mvnw spring-boot:run
cd inventory-service && ./mvnw spring-boot:run
```

Place an order:

```bash
curl -s -X POST http://localhost:8081/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "550e8400-e29b-41d4-a716-446655440000",
    "items": [{
      "productId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "productName": "Milk",
      "quantity": 2,
      "unitPrice": 1.99
    }]
  }'
```

## Running Tests

```bash
cd order-service && ./mvnw test
cd inventory-service && ./mvnw test
```

Tests use real PostgreSQL and RabbitMQ via Testcontainers — no mocks on the infrastructure boundary.

## AWS Deployment

Infrastructure is defined in `/infra` using AWS CDK (TypeScript). Three stacks:

- `QuickCart-Network` — VPC, subnets (public / private / isolated), security groups
- `QuickCart-Data` — RDS Postgres × 2, Amazon MQ (RabbitMQ), ElastiCache Redis
- `QuickCart-Services` — ECR repos, ECS cluster, Fargate services, ALB

```bash
cd infra
npm install
cdk deploy --all
```

CD is handled by GitHub Actions. On push to `master`, the pipeline builds both services, pushes Docker images to ECR, and calls `ecs update-service --force-new-deployment`. OIDC-based authentication — no long-lived AWS credentials in GitHub.

## Repository Structure

```
├── order-service/          Spring Boot — order lifecycle
├── inventory-service/      Spring Boot — stock management
├── infra/                  AWS CDK infrastructure (TypeScript)
├── docs/decisions/         Architecture decision records
├── docker-compose.yml      Local dev infrastructure
└── README.md
```
