package com.blinkitclone.orderservice.domain.model;

import java.util.Set;

/**
 * The Order's lifecycle states. The legal-transition rule lives here, on the
 * enum, rather than as scattered "if status == X" checks in services — so the
 * state machine has exactly one source of truth and cannot be bypassed by
 * calling a setter directly.
 */
public enum OrderStatus {

    CREATED,
    STOCK_RESERVED,
    STOCK_REJECTED,
    PAYMENT_CONFIRMED,
    CANCELLED,
    DELIVERED;

    private static final Set<OrderStatus> CANCELLABLE_FROM = Set.of(CREATED, STOCK_RESERVED);

    public boolean canTransitionTo(OrderStatus next) {
        return switch (this) {
            case CREATED -> next == STOCK_RESERVED || next == STOCK_REJECTED || next == CANCELLED;
            case STOCK_RESERVED -> next == PAYMENT_CONFIRMED || next == CANCELLED;
            case PAYMENT_CONFIRMED -> next == DELIVERED || next == CANCELLED;
            case STOCK_REJECTED, CANCELLED, DELIVERED -> false;
        };
    }

    public boolean isCancellable() {
        return CANCELLABLE_FROM.contains(this);
    }
}
