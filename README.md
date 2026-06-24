# Blinkit-Inspired Order Processing System

A production-style, microservices-based order processing platform built to demonstrate
backend engineering depth: clean architecture, SOLID design, event-driven communication,
resilience patterns, and cloud deployment.

## Why this project exists

This is a portfolio project built to be discussed in depth in Java Backend Engineer
interviews. Every architectural decision below is intentional and documented so it can be
defended, not just demoed.

## System vision

Model the core order lifecycle of a quick-commerce platform (like Blinkit/Zepto):

1. Customer places an order → `order-service`
2. Order triggers a stock reservation check → `inventory-service` (via RabbitMQ event)
3. (Later) Payment is authorized → `payment-service`
4. (Later) Customer is notified → `notification-service`
5. (Later) All requests enter through `api-gateway`

## Why microservices from day one (and the trade-off we accepted)

Most teams start with a modular monolith and extract services once boundaries are proven —
that's the lower-risk path. We deliberately chose microservices from day one instead,
accepting more upfront plumbing (inter-service contracts, eventual consistency, distributed
debugging) in exchange for hands-on experience with the patterns that actually come up in
microservices interviews: event-driven communication, idempotency, sagas, and resilience.

To keep this tractable, we are **not** starting with 6 services. We start with exactly two
(`order-service`, `inventory-service`) talking over RabbitMQ, and only add new services once
the core pattern — domain logic, use cases, event publish/consume, persistence — is proven
and repeatable. Scope is added in phases (see `/docs/ROADMAP.md`).

## Why a monorepo (for now)

All services live in one Git repository during early development. This is a deliberate,
revisitable choice:

- **Pro:** one PR can change a producer and consumer event contract together; one
  `docker-compose.yml` boots the whole local environment; no premature ceremony of separate
  CI pipelines/versioning before there's stable code to version.
- **Con:** in a real org, independent deploy cadence and independent ownership usually push
  teams toward polyrepo. We will discuss this trade-off explicitly in `/docs` once we reach
  the CI/CD phase, and it's a great interview talking point either way.

## Repository structure

```
ORDER/
├── order-service/        Spring Boot service — order lifecycle (Clean Architecture)
├── inventory-service/    Spring Boot service — stock reservation (Clean Architecture)
├── infra/                Local infra config (Postgres init scripts, RabbitMQ defs, etc.)
├── docs/                 Architecture decisions, roadmap, diagrams
├── docker-compose.yml     Local dev infra: Postgres, RabbitMQ, Redis
└── README.md
```

## Local infrastructure

Run the shared infra (Postgres, RabbitMQ, Redis) with:

```bash
docker compose up -d
```

| Service    | Port(s)        | Notes                                   |
|------------|-----------------|------------------------------------------|
| PostgreSQL | 5432            | user: `order_admin` / db: `order_system` |
| RabbitMQ   | 5672, 15672     | Management UI at http://localhost:15672  |
| Redis      | 6379            | No auth in local dev                     |

## Roadmap

See [`docs/ROADMAP.md`](docs/ROADMAP.md) for the full phased plan from setup to AWS
deployment.
