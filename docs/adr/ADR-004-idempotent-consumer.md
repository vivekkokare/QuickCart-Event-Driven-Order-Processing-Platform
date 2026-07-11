# ADR-004: Idempotent Consumer via ProcessedEventStore

**Status:** Accepted  
**Date:** 2026-07

## Context

The outbox poller delivers events at-least-once. RabbitMQ itself can also redeliver messages (e.g. after a consumer restart). inventory-service must not deduct stock twice for the same `OrderCreated` event.

## Decision

inventory-service maintains a `processed_events` table. Before processing any event, it checks whether the event ID has already been processed. If it has, the event is silently acknowledged and discarded. If it hasn't, the event is processed and the ID is recorded — both within the same database transaction.

```java
@Transactional
public void handle(OrderCreatedEvent event) {
    if (processedEventStore.hasBeenProcessed(event.eventId())) {
        log.info("Duplicate event {}, skipping", event.eventId());
        return;
    }
    // ... deduct stock ...
    processedEventStore.markProcessed(event.eventId());
}
```

## Reasons

**At-least-once delivery + idempotent consumer = effectively-once processing.** This is the standard distributed systems pattern for achieving reliable message processing without distributed transactions.

**The check and the stock deduction are atomic.** Both happen in one `@Transactional` method. If the stock deduction fails and the transaction rolls back, the event ID is also not recorded — so the event will be retried. There is no window where stock is deducted but the event is not marked processed (or vice versa).

**Simple and self-contained.** No external deduplication service, no Kafka exactly-once semantics, no CRDT. A single table with a UUID primary key is all that's needed.

## What `eventId` is

Each `OutboxEvent` gets a UUID at creation time in order-service. That UUID travels with the event payload through RabbitMQ to inventory-service. Redeliveries of the same message carry the same UUID, so the idempotency check catches them.

## Trade-offs Accepted

- **`processed_events` table grows unboundedly.** In production, events older than the maximum redelivery window (e.g. 7 days) can be purged safely. Not implemented here.
- **Adds a DB read on every message.** At typical order volumes (hundreds/second) this is negligible. The `event_id` column has a unique index so the lookup is O(log n).
