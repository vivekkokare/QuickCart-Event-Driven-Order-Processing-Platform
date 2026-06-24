package com.blinkitclone.orderservice.application.port.in;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * An "input port" — the contract the outside world (here: the REST controller)
 * calls into the application layer through. The controller depends on this
 * interface, not on the concrete PlaceOrderService class, so the use case can
 * be tested, mocked, or even invoked from a different entry point (a message
 * listener, a CLI, a GraphQL resolver) without the application layer changing
 * at all.
 *
 * <p>The Command/Result types are plain records that belong to the application
 * layer's vocabulary — they are not the same as the API layer's request/response
 * DTOs, even though today they happen to look similar. Keeping them distinct
 * means the REST contract (api.dto) can change shape (versioning, renamed
 * fields) without touching application logic, and vice versa.
 */
public interface PlaceOrderUseCase {

    PlaceOrderResult placeOrder(PlaceOrderCommand command);

    record PlaceOrderCommand(UUID customerId, List<OrderItemCommand> items) {

        public record OrderItemCommand(UUID productId, String productName, int quantity, BigDecimal unitPrice) {
        }
    }

    record PlaceOrderResult(UUID orderId, String status, BigDecimal totalAmount, String currency) {
    }
}
