package com.blinkitclone.orderservice.infrastructure.outbox;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per domain event awaiting delivery to RabbitMQ. Writing this row
 * happens in the SAME database transaction as the business change that
 * produced the event (see OutboxOrderEventPublisher + PlaceOrderService's
 * @Transactional boundary) — so "order saved" and "event recorded" commit
 * or roll back together atomically. A separate process (OutboxRelay) reads
 * unpublished rows and actually sends them to the broker, decoupling
 * "guarantee the event is durably recorded" from "guarantee it reaches
 * RabbitMQ," which is exactly what closes the dual-write gap flagged in
 * Phase 2's RabbitOrderEventPublisher.
 */
@Entity
@Table(name = "outbox_events")
@Getter
@Setter
@NoArgsConstructor
public class OutboxEventEntity {

    @Id
    private UUID id;

    @Column(name = "exchange_name", nullable = false)
    private String exchange;

    @Column(name = "routing_key", nullable = false)
    private String routingKey;

    @Column(name = "payload", nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;
}
