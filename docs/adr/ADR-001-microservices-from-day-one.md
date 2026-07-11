# ADR-001: Microservices Architecture from Day One

**Status:** Accepted  
**Date:** 2026-07

## Context

The system is a Blinkit-inspired order processing platform. The two core domains are:
- **Order management** — accepting orders, validating them, publishing events
- **Inventory management** — tracking stock, reacting to orders, reserving/deducting stock

The question was whether to start with a monolith and extract later, or build as microservices immediately.

## Decision

We chose microservices from day one: `order-service` and `inventory-service` as independently deployable Spring Boot applications.

## Reasons

**Domain boundary is clear and stable.** The order domain and inventory domain have distinct data models, distinct failure modes, and distinct scaling needs. There is no ambiguity about which service owns which data — orders own order aggregates, inventory owns stock aggregates. When the boundary is this clean at the start, the cost of splitting later is mostly overhead (separate repos, CI pipelines, deployment units) rather than untangling mixed data.

**Independent deployability has immediate value.** With two services, a broken inventory deployment does not take down order creation. Order-service degrades gracefully (circuit breaker returns optimistic true) while inventory catches up. A monolith gives you none of this.

**Interview signal.** Microservices decisions demonstrate understanding of bounded contexts (DDD), independent failure domains, and operational trade-offs — all standard system design interview topics.

## Trade-offs Accepted

- **Distributed systems complexity** — network calls, eventual consistency, partial failures. Mitigated by: synchronous pre-flight check (circuit breaker), async event delivery (outbox pattern), idempotent consumer.
- **Operational overhead** — two deployment pipelines, two databases, two log streams. Mitigated by: CDK infrastructure-as-code, shared CI matrix, CloudWatch log groups.
- **No transactions across services** — stock reservation is eventual, not atomic with order creation. Accepted: this matches real-world Blinkit behaviour (order confirmed, stock adjusted asynchronously).

## Alternatives Considered

- **Monolith first, extract later** — rejected because the domain boundary was already clear and the operational infrastructure (Docker, CDK, CI) was being built anyway.
- **Three or more services** — rejected to keep complexity proportional to the learning goal. Payment, delivery tracking, etc. would be natural future additions.
