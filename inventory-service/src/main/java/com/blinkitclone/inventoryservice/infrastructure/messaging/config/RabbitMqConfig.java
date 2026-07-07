package com.blinkitclone.inventoryservice.infrastructure.messaging.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.util.Map;

/**
 * Declares the full messaging topology, including the dead-letter path that
 * was an explicitly documented gap after Phase 2.
 *
 * <p>How a poison message is handled end-to-end:
 * <ol>
 *   <li>OrderCreatedEventListener throws (e.g. a transient DB outage).</li>
 *   <li>retryRabbitListenerContainerFactory's interceptor retries the
 *       delivery in-process up to 3 times with a fixed backoff - no
 *       redelivery from the broker involved yet, this is local retry.</li>
 *   <li>If all 3 attempts fail, RejectAndDontRequeueRecoverer rejects the
 *       message (basic.reject, requeue=false) instead of letting the
 *       exception propagate back to the broker as a requeue.</li>
 *   <li>Because stockReservationQueue declares
 *       x-dead-letter-exchange/x-dead-letter-routing-key, RabbitMQ
 *       automatically routes the rejected message to the DLX, which lands
 *       it in stockReservationDlq for manual inspection/replay - instead of
 *       redelivering forever and starving the queue.</li>
 * </ol>
 */
@Configuration
public class RabbitMqConfig {

    public static final String ORDER_EVENTS_EXCHANGE = "order.events";
    public static final String ORDER_CREATED_ROUTING_KEY = "order.created";
    public static final String STOCK_RESERVATION_QUEUE = "inventory.order-created.queue";

    public static final String DEAD_LETTER_EXCHANGE = "order.events.dlx";
    public static final String DEAD_LETTER_ROUTING_KEY = "order.created.dead";
    public static final String STOCK_RESERVATION_DLQ = "inventory.order-created.dlq";

    @Bean
    public TopicExchange orderEventsExchange() {
        return new TopicExchange(ORDER_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue stockReservationQueue() {
        return new Queue(STOCK_RESERVATION_QUEUE, true, false, false, Map.of(
                "x-dead-letter-exchange", DEAD_LETTER_EXCHANGE,
                "x-dead-letter-routing-key", DEAD_LETTER_ROUTING_KEY));
    }

    @Bean
    public Queue stockReservationDlq() {
        return new Queue(STOCK_RESERVATION_DLQ, true);
    }

    @Bean
    public Binding stockReservationBinding(Queue stockReservationQueue, TopicExchange orderEventsExchange) {
        return BindingBuilder.bind(stockReservationQueue).to(orderEventsExchange).with(ORDER_CREATED_ROUTING_KEY);
    }

    @Bean
    public Binding stockReservationDlqBinding(Queue stockReservationDlq, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(stockReservationDlq).to(deadLetterExchange).with(DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * Bounded local retry (3 attempts, 1s fixed backoff) before a message is
     * rejected to the DLQ. Stateless retry is correct here because
     * ReserveStockService's idempotency check makes re-running the same
     * message safe - there is no in-memory state from attempt 1 that attempt
     * 2 needs to see.
     */
    @Bean
    public SimpleRabbitListenerContainerFactory retryRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setAdviceChain(retryInterceptor());
        return factory;
    }

    private RetryOperationsInterceptor retryInterceptor() {
        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(new SimpleRetryPolicy(3));

        org.springframework.retry.backoff.FixedBackOffPolicy backOffPolicy =
                new org.springframework.retry.backoff.FixedBackOffPolicy();
        backOffPolicy.setBackOffPeriod(1000);
        retryTemplate.setBackOffPolicy(backOffPolicy);

        return RetryInterceptorBuilder.stateless()
                .retryOperations(retryTemplate)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }
}
