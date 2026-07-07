package com.blinkitclone.orderservice.infrastructure.outbox;

import com.blinkitclone.orderservice.application.port.out.OrderEventPublisher;
import com.blinkitclone.orderservice.domain.model.Order;
import com.blinkitclone.orderservice.domain.model.OrderItem;
import com.blinkitclone.orderservice.infrastructure.messaging.RabbitMqConfig;
import com.blinkitclone.orderservice.infrastructure.messaging.event.OrderCreatedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Implements OrderEventPublisher by writing to the outbox table instead of
 * talking to RabbitMQ directly. Because this is called from inside
 * PlaceOrderService's @Transactional method, this insert commits atomically
 * with the order's own insert — there is no window where the order exists
 * but the event doesn't, or vice versa. Actual delivery to the broker is
 * OutboxRelay's job, on its own schedule.
 */
@Component
public class OutboxOrderEventPublisher implements OrderEventPublisher {

    private final OutboxEventJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxOrderEventPublisher(OutboxEventJpaRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publishOrderCreated(Order order) {
        UUID eventId = UUID.randomUUID();
        OrderCreatedEvent event = toEvent(eventId, order);

        OutboxEventEntity outboxEvent = new OutboxEventEntity();
        outboxEvent.setId(eventId);
        outboxEvent.setExchange(RabbitMqConfig.ORDER_EVENTS_EXCHANGE);
        outboxEvent.setRoutingKey(RabbitMqConfig.ORDER_CREATED_ROUTING_KEY);
        outboxEvent.setPayload(serialize(event));
        outboxEvent.setCreatedAt(Instant.now());

        outboxRepository.save(outboxEvent);
    }

    private String serialize(OrderCreatedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize OrderCreatedEvent", e);
        }
    }

    private OrderCreatedEvent toEvent(UUID eventId, Order order) {
        var items = order.items().stream().map(this::toItemPayload).toList();
        var total = order.totalAmount();

        return new OrderCreatedEvent(
                eventId,
                order.id().value(),
                order.customerId(),
                items,
                total.amount(),
                total.currency(),
                Instant.now());
    }

    private OrderCreatedEvent.OrderItemPayload toItemPayload(OrderItem item) {
        return new OrderCreatedEvent.OrderItemPayload(item.productId(), item.productName(), item.quantity());
    }
}
