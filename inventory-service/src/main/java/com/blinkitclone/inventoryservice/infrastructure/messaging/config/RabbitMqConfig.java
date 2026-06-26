package com.blinkitclone.inventoryservice.infrastructure.messaging.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * inventory-service declares the exchange it depends on defensively (with
 * identical properties to order-service's declaration) so this service can
 * start up cleanly and bind its queue even if it comes up before
 * order-service ever has. RabbitMQ exchange/queue declarations are
 * idempotent as long as both declarations agree on the arguments - if they
 * ever disagree, the broker rejects the second declaration loudly at
 * startup, which is a deliberate fail-fast rather than a silent mismatch.
 *
 * <p>Known gap, deferred to Phase 3: there is no dead-letter queue configured
 * yet. As-is, if OrderCreatedEventListener throws, Spring AMQP's default
 * behaviour requeues the message and it is redelivered indefinitely - a
 * "poison message" can loop forever and starve the queue. Phase 3 adds a
 * dead-letter exchange/queue plus a bounded retry policy to fix this.
 */
@Configuration
public class RabbitMqConfig {

    public static final String ORDER_EVENTS_EXCHANGE = "order.events";
    public static final String ORDER_CREATED_ROUTING_KEY = "order.created";
    public static final String STOCK_RESERVATION_QUEUE = "inventory.order-created.queue";

    @Bean
    public TopicExchange orderEventsExchange() {
        return new TopicExchange(ORDER_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public Queue stockReservationQueue() {
        return new Queue(STOCK_RESERVATION_QUEUE, true);
    }

    @Bean
    public Binding stockReservationBinding(Queue stockReservationQueue, TopicExchange orderEventsExchange) {
        return BindingBuilder.bind(stockReservationQueue).to(orderEventsExchange).with(ORDER_CREATED_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
