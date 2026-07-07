package com.blinkitclone.orderservice.application.port.out;

import java.util.UUID;

/**
 * Output port that lets the order placement use case perform a pre-flight
 * stock availability check against inventory-service before accepting an order.
 *
 * <p>This is a synchronous optimistic check, not the authoritative reservation.
 * The actual reservation happens asynchronously via the OrderCreated event flow.
 * The check exists for fast UX: reject obviously impossible orders immediately
 * instead of letting them queue and fail minutes later.
 *
 * <p>Implementations must handle the case where inventory-service is unavailable
 * and fall back to returning {@code true} — if we can't check, we accept the
 * order optimistically and let the reservation step make the final call.
 */
public interface StockAvailabilityPort {

    /**
     * Returns {@code true} if at least {@code requiredQuantity} units of
     * {@code productId} are available according to inventory-service's last
     * known state, or if inventory-service is unreachable (optimistic fallback).
     */
    boolean isStockAvailable(UUID productId, int requiredQuantity);
}
