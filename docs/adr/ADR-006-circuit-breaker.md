# ADR-006: Circuit Breaker with Optimistic Fallback for Inventory Pre-flight Check

**Status:** Accepted  
**Date:** 2026-07

## Context

order-service calls inventory-service synchronously before placing an order to check stock availability. If inventory-service is down, a naive implementation would either:
- Fail every order (bad UX, cascading failure)
- Have no pre-check at all (risk of overselling)

## Decision

We added a Resilience4j `@CircuitBreaker` on `InventoryServiceClient.isStockAvailable()` with an **optimistic fallback**: when the circuit is open (inventory-service is unreachable), return `true` — allow the order to proceed.

```java
@CircuitBreaker(name = "inventoryService", fallbackMethod = "fallbackIsStockAvailable")
public boolean isStockAvailable(UUID productId, int qty) {
    // HTTP call to inventory-service
}

private boolean fallbackIsStockAvailable(UUID productId, int qty, Exception ex) {
    log.warn("Circuit breaker active, allowing order optimistically");
    return true;
}
```

Circuit breaker configuration:
- Window: 5 calls
- Failure threshold: 50%
- Open duration: 10 seconds
- Half-open probe: 2 calls

## Reasons

**Availability over consistency for the pre-flight check.** The pre-flight is a best-effort guard, not a hard lock. Stock is definitively reserved by inventory-service when it processes the `OrderCreated` event. If inventory-service is temporarily down, it is better to accept the order and let it fail at the reservation step than to reject every order during the outage.

**Circuit breaker prevents thundering herd.** Without it, every order request during an inventory-service outage makes an HTTP call that times out after (e.g.) 10 seconds. With a circuit breaker, after 3 failures the circuit opens and subsequent calls short-circuit immediately — protecting both order-service threads and the recovering inventory-service.

**Graceful degradation is a system design interview core concept.** Being able to explain "we chose optimistic availability over strict consistency here because the downstream is idempotent and will self-correct" directly maps to CAP theorem trade-off discussions.

## Why Optimistic (Allow) Rather Than Pessimistic (Reject)?

The inventory-service event handler is the true gate. It will:
1. Check real stock levels from the database
2. Reject the reservation if stock is actually insufficient
3. Potentially trigger a compensating event (order cancellation) — a natural extension

Rejecting orders at the pre-flight level during an inventory outage would silently drop valid orders. Optimistic acceptance means worst-case the customer sees a later cancellation — same as real-world Blinkit behaviour.

## Trade-offs Accepted

- **Possible overselling during inventory outage.** Mitigated by the inventory event handler as the final authority on stock, and by the short circuit-open window (10 seconds).
- **Pre-flight becomes advisory during circuit-open.** Accepted — it was always advisory since it checks a cached/eventually-consistent stock level anyway.
