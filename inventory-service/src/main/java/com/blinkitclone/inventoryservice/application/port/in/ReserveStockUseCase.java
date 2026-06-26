package com.blinkitclone.inventoryservice.application.port.in;

import java.util.List;
import java.util.UUID;

/**
 * Input port triggered when an order is created. Modeled as a request for
 * "all items in this order, reserved together" rather than one call per
 * item — the use case decides all-or-nothing reservation for the order
 * (see ReserveStockService), which is a deliberately simpler starting model
 * than partial fulfilment. Partial fulfilment (reserve what's available,
 * report shortfalls per item) is a realistic future extension but adds
 * complexity not needed to demonstrate the core event-driven pattern yet.
 */
public interface ReserveStockUseCase {

    ReservationResult reserveStock(ReserveStockCommand command);

    record ReserveStockCommand(UUID orderId, List<ReservationItem> items) {

        public record ReservationItem(UUID productId, int quantity) {
        }
    }

    record ReservationResult(UUID orderId, boolean reserved, String reason) {

        public static ReservationResult success(UUID orderId) {
            return new ReservationResult(orderId, true, null);
        }

        public static ReservationResult rejected(UUID orderId, String reason) {
            return new ReservationResult(orderId, false, reason);
        }
    }
}
