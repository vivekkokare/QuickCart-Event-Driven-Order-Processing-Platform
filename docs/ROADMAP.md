# Roadmap

Each phase below is built across one or more mentoring sessions. We do not move to the next
phase until the current one's code, tests, and the *why* behind it are solid.

## Phase 0 — Foundations & repo setup (current)
- Monorepo skeleton, `.gitignore`, root README
- Docker Compose for local infra: Postgres, RabbitMQ, Redis
- Conventions: commit style, package layout, Java/Spring versions

## Phase 1 — `order-service` core domain
- Clean Architecture layers: domain, application, infrastructure, api
- `Order` aggregate, value objects, domain invariants
- Use cases (application services) decoupled from frameworks
- PostgreSQL persistence via Spring Data JPA, repository pattern
- REST API for placing/viewing orders
- Unit tests (domain, use cases) + integration tests (repository, API)

## Phase 2 — `inventory-service` + async messaging
- Second bounded context: stock levels, reservations
- RabbitMQ event contracts: `OrderCreated` → `StockReserved` / `StockRejected`
- Idempotent consumers, outbox pattern for reliable event publishing

## Phase 3 — Resilience & caching
- Redis caching (e.g. product/stock lookups)
- Resilience4j: retries, circuit breakers, timeouts
- Dead-letter queues for poison messages
- Structured logging + correlation IDs across services

## Phase 4 — `payment-service` & sagas
- Orchestration-based saga across order → inventory → payment
- Compensating transactions (release stock on payment failure)
- Discussion: why not distributed (2PC) transactions

## Phase 5 — `api-gateway` & cross-cutting concerns
- Spring Cloud Gateway as single entry point
- JWT authentication, rate limiting
- Centralized exception handling / error contract

## Phase 6 — CI/CD with GitHub Actions
- Per-service build/test/lint pipelines
- Docker image build & push
- Strategy for monorepo multi-service pipelines (path filters)

## Phase 7 — AWS deployment
- Compute: ECS vs EKS decision (with trade-offs documented)
- RDS (Postgres), Amazon MQ or self-managed RabbitMQ, ElastiCache (Redis)
- Secrets management, environment configuration
- (Optional) Infra-as-code with Terraform

## Phase 8 — Interview readiness
- Architecture diagrams (C4-style)
- Documented trade-offs for every major decision
- Mock interview Q&A walkthrough of the whole system
