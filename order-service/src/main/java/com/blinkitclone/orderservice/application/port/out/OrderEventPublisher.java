package com.blinkitclone.orderservice.application.port.out;

import com.blinkitclone.orderservice.domain.model.Order;

/**
 * Output port for publishing domain events about an Order. Like
 * OrderRepository, this keeps the application layer ignorant of *how*
 * events are delivered (RabbitMQ today; could be Kafka, an outbox table,
 * or nothing in a test) — it only knows it can ask for an event to be
 * published once an Order has been created.
 */
public interface OrderEventPublisher {

    void publishOrderCreated(Order order);
}
