# ADR-005: Redis Caching via Decorator Pattern

**Status:** Accepted  
**Date:** 2026-07

## Context

inventory-service's stock check (`GET /api/v1/stock/{productId}`) is called synchronously by order-service on every order placement. At Blinkit scale, this endpoint is on the hot path. Every cache miss hits Postgres.

We needed to add Redis caching without modifying the domain model or the core repository adapter.

## Decision

We used the Decorator pattern:

- `StockRepositoryAdapter` — the real JPA-backed repository (implements `StockRepository`)
- `CachingStockRepository` — a `@Primary @Component` that wraps `StockRepositoryAdapter` and adds Redis read-through/write-around caching (also implements `StockRepository`)

The domain and use cases depend only on the `StockRepository` interface. Spring injects `CachingStockRepository` because it is `@Primary`. The JPA adapter is injected into the caching decorator. Neither the domain nor any use case knows caching exists.

## Reasons

**Open/Closed Principle.** The `StockRepositoryAdapter` is not modified. The caching behaviour is added by wrapping it. This is textbook OCP.

**Domain model stays clean.** We introduced `StockCacheEntry` (a record) as the cache DTO. The domain `Stock` class has no Jackson annotations, no `@JsonDeserialize`, no `serialVersionUID`. Serialisation concerns live entirely in the infrastructure layer.

**Cache can be disabled or replaced without touching business logic.** Remove `@Primary` from `CachingStockRepository` and the system falls back to direct DB reads. Swap Redis for Memcached — only the infrastructure layer changes.

**Why programmatic caching over `@Cacheable`?**
`@Cacheable` works on `Stock` objects but requires Jackson annotations on the domain model. Programmatic caching via `CacheManager` lets us convert `Stock` → `StockCacheEntry` before writing and `StockCacheEntry` → `Stock` after reading, keeping the domain annotation-free.

## Cache Strategy

| Operation | Strategy | Reason |
|---|---|---|
| `findByProductId` | Read-through | Hot path — check cache first, populate on miss |
| `save` | Write-around (evict) | Write to DB first, evict stale cache entry |
| `saveAll` | Not cached | Bulk operations used only during stock seeding |
| TTL | 5 minutes | Balance between freshness and DB load |

## Trade-offs Accepted

- **Stale reads possible within TTL window.** If stock is deducted and the cache isn't evicted fast enough, a subsequent order could see stale available quantity. Mitigated by evicting on every `save`. In a distributed cache with multiple inventory-service instances, this window can be tighter with a write-through strategy.
- **Cache adds a network hop on misses.** Redis on ElastiCache in the same VPC adds ~1ms. Acceptable.
