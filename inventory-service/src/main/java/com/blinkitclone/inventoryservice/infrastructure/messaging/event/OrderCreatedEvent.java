package com.blinkitclone.inventoryservice.infrastructure.messaging.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * inventory-service's own copy of the OrderCreated wire contract — see
 * docs/decisions/0001-event-contracts-not-shared-library.md for why this is
 * not imported from order-service. Note inventory-service does not even
 * need totalAmount/currency for its own logic; they're kept here anyway so
 * this copy matches the documented schema exactly. A consumer is free to
 * ignore fields it doesn't need, which is itself a form of resilience to
 * future schema growth (adding a field is non-breaking).
 *
 * <p>Exchange: "order.events" (topic) | Routing key: "order.created"
 */
public record OrderCreatedEvent(
        UUID orderId,
        UUID customerId,
        List<OrderItemPayload> items,
        BigDecimal totalAmount,
        String currency,
        Instant occurredAt) {

    public record OrderItemPayload(UUID productId, String productName, int quantity) {
    }
}
