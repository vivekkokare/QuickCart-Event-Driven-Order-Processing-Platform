package com.blinkitclone.inventoryservice.infrastructure.messaging;

import com.blinkitclone.inventoryservice.application.port.in.ReserveStockUseCase;
import com.blinkitclone.inventoryservice.application.port.in.ReserveStockUseCase.ReserveStockCommand;
import com.blinkitclone.inventoryservice.application.port.in.ReserveStockUseCase.ReserveStockCommand.ReservationItem;
import com.blinkitclone.inventoryservice.application.port.in.ReserveStockUseCase.ReservationResult;
import com.blinkitclone.inventoryservice.infrastructure.messaging.config.RabbitMqConfig;
import com.blinkitclone.inventoryservice.infrastructure.messaging.event.OrderCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * The entry point that drives ReserveStockUseCase from a RabbitMQ message
 * instead of an HTTP request - mirroring how OrderController drives
 * PlaceOrderUseCase from HTTP in order-service. This is the payoff of the
 * input-port abstraction: the use case has no idea whether it was triggered
 * by a REST call or a queue message.
 *
 * <p>Redelivery safety: if this message is redelivered (broker restart
 * before the ack lands, or a retry after a transient failure - see
 * RabbitMqConfig's retry/DLQ setup), ReserveStockService's idempotency check
 * against ProcessedEventStore means a duplicate delivery is a guaranteed
 * no-op rather than a double-decrement.
 */
@Component
public class OrderCreatedEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedEventListener.class);

    private final ReserveStockUseCase reserveStockUseCase;

    public OrderCreatedEventListener(ReserveStockUseCase reserveStockUseCase) {
        this.reserveStockUseCase = reserveStockUseCase;
    }

    @RabbitListener(
            queues = RabbitMqConfig.STOCK_RESERVATION_QUEUE,
            containerFactory = "retryRabbitListenerContainerFactory")
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("Received OrderCreated for order {}", event.orderId());

        var items = event.items().stream()
                .map(item -> new ReservationItem(item.productId(), item.quantity()))
                .toList();

        ReservationResult result = reserveStockUseCase.reserveStock(
                new ReserveStockCommand(event.eventId(), event.orderId(), items));

        if (result.reserved()) {
            log.info("Stock reserved for order {}", event.orderId());
        } else {
            log.warn("Stock reservation rejected for order {}: {}", event.orderId(), result.reason());
        }
    }
}
