# ADR-003: Transactional Outbox Pattern for Reliable Event Publishing

**Status:** Accepted  
**Date:** 2026-07

## Context

order-service must publish an `OrderCreated` event to RabbitMQ after persisting an order. The naive approach is:

```java
orderRepository.save(order);           // DB write
rabbitTemplate.convertAndSend(event);  // broker write
```

This has a critical failure mode: if the application crashes between the two lines, or if the broker is temporarily unavailable, the order is saved but the event is never published. inventory-service never deducts stock. The system is silently inconsistent.

## Decision

We implemented the Transactional Outbox Pattern:

1. Within the same database transaction that saves the `Order`, also insert an `OutboxEvent` row.
2. A separate `@Scheduled` poller reads unpublished outbox events and publishes them to RabbitMQ.
3. On successful publish, the event is marked `PUBLISHED`.

The outbox write and the order write share a single ACID transaction. Either both succeed or neither does. The broker write is decoupled and retried independently.

## Reasons

**Atomicity between DB write and event publication.** The database is the source of truth. Writing the outbox entry in the same transaction as the order means we can never save an order without also guaranteeing an event will eventually be published — even if the broker is down for an hour.

**At-least-once delivery.** The poller retries on failure. Combined with an idempotent consumer on the inventory side (see ADR-004), this gives reliable exactly-once processing semantics.

**No two-phase commit or distributed transactions required.** 2PC is complex, slow, and rarely supported by modern brokers. The outbox pattern achieves the same safety guarantee using only the database's local ACID properties.

## Implementation Detail

```java
// PlaceOrderService — one transaction, two writes
@Transactional
public OrderId placeOrder(PlaceOrderCommand cmd) {
    Order order = Order.create(...);
    orderRepository.save(order);
    outboxRepository.save(OutboxEvent.from(order));  // same TX
    return order.id();
}

// OutboxPoller — separate transaction, retryable
@Scheduled(fixedDelay = 5000)
public void publishPending() {
    outboxRepository.findUnpublished().forEach(event -> {
        rabbitTemplate.convertAndSend(event.payload());
        event.markPublished();
        outboxRepository.save(event);
    });
}
```

## Trade-offs Accepted

- **Eventual consistency** — there is a small window (up to 5 seconds) between order creation and event publication. This is acceptable for an order processing system where stock deduction is inherently asynchronous.
- **Polling overhead** — the poller hits the DB every 5 seconds. At low volume this is negligible. At high volume, CDC (Change Data Capture) with Debezium is the production-grade alternative, but adds significant operational complexity.
- **Outbox table grows** — needs periodic cleanup of old `PUBLISHED` events. Omitted here but trivial to add.
