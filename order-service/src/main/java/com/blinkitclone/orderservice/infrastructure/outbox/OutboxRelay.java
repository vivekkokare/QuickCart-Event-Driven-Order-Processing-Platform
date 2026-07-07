package com.blinkitclone.orderservice.infrastructure.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * Polls the outbox table for unpublished rows and relays them to RabbitMQ.
 * This is intentionally a simple polling relay (not Debezium/CDC reading the
 * WAL) — appropriate for this project's scale, and the standard "basic
 * outbox" implementation referenced in most outbox-pattern write-ups.
 *
 * <p>Failure handling: each row is published independently; if RabbitMQ is
 * unreachable, the row simply stays unpublished and is retried on the next
 * tick — at-least-once delivery, which is why the consumer side must be
 * idempotent (see inventory-service's ProcessedEvent check).
 *
 * <p>Scaling note: with multiple order-service instances, two relays could
 * race to publish the same row. We accept that here (the consumer is
 * idempotent regardless), but the standard fix is a `SELECT ... FOR UPDATE
 * SKIP LOCKED` query instead of a plain findTop50 — left as a documented
 * follow-up since this project runs a single instance locally.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventJpaRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;

    public OutboxRelay(OutboxEventJpaRepository outboxRepository, RabbitTemplate rabbitTemplate) {
        this.outboxRepository = outboxRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(fixedDelay = 2000)
    public void relayPendingEvents() {
        List<OutboxEventEntity> pending = outboxRepository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();

        for (OutboxEventEntity event : pending) {
            try {
                publish(event);
                event.setPublishedAt(Instant.now());
                outboxRepository.save(event);
            } catch (Exception e) {
                log.error("Failed to relay outbox event {}, will retry next tick", event.getId(), e);
            }
        }
    }

    @Transactional
    void publish(OutboxEventEntity event) {
        Message message = new Message(
                event.getPayload().getBytes(StandardCharsets.UTF_8),
                buildMessageProperties());
        rabbitTemplate.send(event.getExchange(), event.getRoutingKey(), message);
    }

    private MessageProperties buildMessageProperties() {
        MessageProperties properties = new MessageProperties();
        properties.setContentType("application/json");
        return properties;
    }
}
