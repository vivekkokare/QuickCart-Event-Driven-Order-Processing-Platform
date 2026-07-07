package com.blinkitclone.orderservice.infrastructure.outbox;

import com.blinkitclone.orderservice.domain.model.Money;
import com.blinkitclone.orderservice.domain.model.Order;
import com.blinkitclone.orderservice.domain.model.OrderItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mockito is used here for OutboxEventJpaRepository (a Spring Data
 * interface with dozens of inherited methods, impractical to hand-write a
 * fake for) — different from PlaceOrderServiceTest's hand-rolled fake of
 * OrderRepository, which is a small application-layer port. The distinction:
 * mock framework boundaries you don't own; hand-write fakes for the small
 * ports you defined yourself.
 */
class OutboxOrderEventPublisherTest {

    private final OutboxEventJpaRepository outboxRepository = mock(OutboxEventJpaRepository.class);
    // Registers JavaTimeModule explicitly, matching what Spring Boot's
    // autoconfigured ObjectMapper bean does for us in production - a bare
    // `new ObjectMapper()` cannot serialize Instant and throws.
    private final OutboxOrderEventPublisher publisher =
            new OutboxOrderEventPublisher(outboxRepository, new ObjectMapper().registerModule(new JavaTimeModule()));

    @Test
    void publishingWritesAnOutboxRowWithSerializedPayload() {
        Order order = Order.place(UUID.randomUUID(), List.of(
                OrderItem.of(UUID.randomUUID(), "Milk 1L", 2, Money.of(new BigDecimal("55.00"), "INR"))));

        when(outboxRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        publisher.publishOrderCreated(order);

        verify(outboxRepository).save(any(OutboxEventEntity.class));
    }

    @Test
    void serializedPayloadContainsTheOrderId() {
        Order order = Order.place(UUID.randomUUID(), List.of(
                OrderItem.of(UUID.randomUUID(), "Bread", 1, Money.of(new BigDecimal("40.00"), "INR"))));

        publisher.publishOrderCreated(order);

        var captor = org.mockito.ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxRepository).save(captor.capture());

        assertThat(captor.getValue().getPayload()).contains(order.id().value().toString());
        assertThat(captor.getValue().getExchange()).isEqualTo("order.events");
        assertThat(captor.getValue().getRoutingKey()).isEqualTo("order.created");
        assertThat(captor.getValue().getPublishedAt()).isNull();
    }
}
