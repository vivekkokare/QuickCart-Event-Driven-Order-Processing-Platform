package com.blinkitclone.orderservice.infrastructure.messaging;

import com.blinkitclone.orderservice.application.port.out.OrderEventPublisher;
import com.blinkitclone.orderservice.domain.model.Order;
import com.blinkitclone.orderservice.domain.model.OrderItem;
import com.blinkitclone.orderservice.infrastructure.messaging.event.OrderCreatedEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * RabbitMQ implementation of the OrderEventPublisher port. Translates the
 * domain Order into the wire-format OrderCreatedEvent and publishes it to
 * the order.events topic exchange.
 *
 * <p>Known limitation, by design at this phase: this publish happens as a
 * separate step after the use case's database transaction has already
 * committed (see PlaceOrderService). If the process crashes between the
 * commit and this publish call, inventory-service never learns about the
 * order — a "dual write" problem. The fix is the transactional outbox
 * pattern, planned for Phase 3, where the event is written to an outbox
 * table in the *same* transaction as the order and a separate relay process
 * publishes it. We are accepting this gap for now to keep this phase
 * focused on the messaging plumbing itself.
 */
@Component
public class RabbitOrderEventPublisher implements OrderEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public RabbitOrderEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publishOrderCreated(Order order) {
        OrderCreatedEvent event = toEvent(order);
        rabbitTemplate.convertAndSend(
                RabbitMqConfig.ORDER_EVENTS_EXCHANGE,
                RabbitMqConfig.ORDER_CREATED_ROUTING_KEY,
                event);
    }

    private OrderCreatedEvent toEvent(Order order) {
        var items = order.items().stream()
                .map(this::toItemPayload)
                .toList();
        var total = order.totalAmount();

        return new OrderCreatedEvent(
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
