package com.blinkitclone.orderservice.infrastructure.messaging;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the RabbitMQ topology this service publishes into. order-service
 * owns the exchange (the producer is responsible for declaring exchanges it
 * publishes to); inventory-service separately declares its own queue and
 * binds it to this exchange by name and routing key — the two services never
 * share a reference to a Java object, only the agreed-upon names below.
 */
@Configuration
public class RabbitMqConfig {

    public static final String ORDER_EVENTS_EXCHANGE = "order.events";
    public static final String ORDER_CREATED_ROUTING_KEY = "order.created";

    @Bean
    public TopicExchange orderEventsExchange() {
        return new TopicExchange(ORDER_EVENTS_EXCHANGE, true, false);
    }

    /**
     * JSON instead of Java serialization: the message body must be readable by
     * any consumer regardless of language/classpath, which is the whole point
     * of choosing duplicated-schema-over-shared-library (see ADR 0001).
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
