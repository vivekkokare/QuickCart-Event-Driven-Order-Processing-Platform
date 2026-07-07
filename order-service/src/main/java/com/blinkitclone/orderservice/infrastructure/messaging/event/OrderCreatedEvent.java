package com.blinkitclone.orderservice.infrastructure.messaging.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The wire contract published when an order is created. This is the
 * *integration contract* between order-service and inventory-service —
 * deliberately NOT a shared Java class imported by both services (see
 * docs/decisions for why). Each service defines its own copy of this shape;
 * what keeps them in sync is the documented JSON schema and routing key
 * below, not a shared compile-time dependency.
 *
 * <p>Exchange: "order.events" (topic) | Routing key: "order.created"
 *
 * <p>Versioning note: any backwards-incompatible change to this shape (field
 * removed/retyped) requires a new routing key (e.g. "order.created.v2") so
 * consumers can migrate independently rather than breaking on deploy.
 *
 * <p>eventId is the outbox row's own id (see infrastructure.outbox) and
 * doubles as the idempotency key consumers use to detect redelivery.
 */
public record OrderCreatedEvent(
        UUID eventId,
        UUID orderId,
        UUID customerId,
        List<OrderItemPayload> items,
        BigDecimal totalAmount,
        String currency,
        Instant occurredAt) {

    public record OrderItemPayload(UUID productId, String productName, int quantity) {
    }
}
