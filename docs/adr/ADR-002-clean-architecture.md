# ADR-002: Clean Architecture with Ports and Adapters

**Status:** Accepted  
**Date:** 2026-07

## Context

Each service needed an internal structure. Options ranged from a flat package-by-layer layout to a strict hexagonal architecture.

## Decision

We adopted Clean Architecture (Ports and Adapters / Hexagonal) with four layers per service:

```
domain/          — pure Java, zero framework dependencies
application/     — use cases, port interfaces (in/out)
infrastructure/  — adapters: JPA, RabbitMQ, Redis, HTTP clients
api/             — REST controllers, DTOs, exception handlers
```

Dependency rule: outer layers depend on inner layers, never the reverse. The `domain` package has no Spring annotations, no JPA annotations, no Jackson annotations.

## Reasons

**The domain model stays testable without infrastructure.** `PlaceOrderService` tests run in milliseconds with no database, no broker, no HTTP server — just pure Java fakes for the output ports. This is impossible in a layered architecture where services directly call repositories.

**Framework changes don't ripple into business logic.** If we swap JPA for JDBC, or RabbitMQ for Kafka, only the `infrastructure` package changes. The use cases are untouched.

**Ports make dependencies explicit.** `OrderRepository`, `EventPublisher`, `StockAvailabilityPort` — each is an interface defined in `application/port`. The domain never knows that orders are stored in Postgres or that events go to RabbitMQ. This is the Dependency Inversion Principle applied architecturally.

**Interview signal.** Being able to explain why `@Entity` is not on the domain `Order` class — "to keep the domain model free of persistence concerns so it can be tested in isolation" — immediately distinguishes you from candidates who just put `@Entity` everywhere.

## Concrete Example

```
domain/model/Order.java          — no annotations, factory method, business rules
application/port/in/PlaceOrderUseCase.java   — input port (interface)
application/port/out/OrderRepository.java    — output port (interface)
application/usecase/PlaceOrderService.java   — implements use case, calls output ports
infrastructure/persistence/OrderRepositoryAdapter.java — implements OrderRepository via JPA
api/OrderController.java         — calls PlaceOrderUseCase
```

## Trade-offs Accepted

- **More files and indirection** than a simple service/repository pattern. For a two-domain system this is verbosity without complexity — worth it for the architectural discipline it enforces.
- **`@Primary` + decorator pattern** needed for Redis caching layer (`CachingStockRepository`) to avoid modifying the core adapter. This is a legitimate complexity cost that pays off when the cache needs to be swapped or disabled.
