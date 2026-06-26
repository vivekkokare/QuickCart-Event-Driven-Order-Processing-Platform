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
 * <p>Idempotency gap, deferred to Phase 3: if this message is redelivered
 * (e.g. after a broker restart before the ack lands), reserveStock runs
 * again and may double-decrement stock. The fix is an idempotency table
 * keyed by orderId, checked before processing - intentionally not built yet
 * to keep this phase focused on the base publish/consume wiring.
 */
@Component
public class OrderCreatedEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedEventListener.class);

    private final ReserveStockUseCase reserveStockUseCase;

    public OrderCreatedEventListener(ReserveStockUseCase reserveStockUseCase) {
        this.reserveStockUseCase = reserveStockUseCase;
    }

    @RabbitListener(queues = RabbitMqConfig.STOCK_RESERVATION_QUEUE)
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("Received OrderCreated for order {}", event.orderId());

        var items = event.items().stream()
                .map(item -> new ReservationItem(item.productId(), item.quantity()))
                .toList();

        ReservationResult result = reserveStockUseCase.reserveStock(new ReserveStockCommand(event.orderId(), items));

        if (result.reserved()) {
            log.info("Stock reserved for order {}", event.orderId());
        } else {
            log.warn("Stock reservation rejected for order {}: {}", event.orderId(), result.reason());
        }
    }
}
