package com.blinkitclone.orderservice.domain.model;

import com.blinkitclone.orderservice.domain.exception.EmptyOrderException;
import com.blinkitclone.orderservice.domain.exception.InvalidOrderStateTransitionException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate root for the order bounded context.
 *
 * <p>An "aggregate" in DDD terms is a cluster of objects (here: Order + its
 * OrderItems) that must always be changed together as one consistency
 * boundary. The Order is the only entry point — callers never reach into
 * orderItems directly to mutate them, they call methods on Order, which
 * enforces every invariant (non-empty items, legal status transitions,
 * non-negative totals) on every change. This is what makes the domain model
 * "rich" rather than an anaemic data bag with getters/setters.
 *
 * <p>Deliberately framework-free: no JPA annotations, no Spring annotations.
 * This class has zero compile-time dependency on persistence or web concerns,
 * which is the central promise of Clean Architecture — the domain doesn't
 * know that PostgreSQL, Spring, or REST exist.
 */
public final class Order {

    private final OrderId id;
    private final UUID customerId;
    private final List<OrderItem> items;
    private OrderStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    private Order(OrderId id, UUID customerId, List<OrderItem> items, OrderStatus status,
                  Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.customerId = customerId;
        this.items = new ArrayList<>(items);
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** Factory for placing a brand-new order. Enforces invariants at creation time. */
    public static Order place(UUID customerId, List<OrderItem> items) {
        Objects.requireNonNull(customerId, "customerId must not be null");
        if (items == null || items.isEmpty()) {
            throw new EmptyOrderException();
        }
        Instant now = Instant.now();
        return new Order(OrderId.newId(), customerId, items, OrderStatus.CREATED, now, now);
    }

    /**
     * Reconstitution factory — used by the persistence adapter to rebuild a domain
     * Order from data already stored in the database. No invariant re-validation
     * beyond null checks: trusting that data which already made it through `place`
     * once is still valid is intentional here, and keeps this path cheap.
     */
    public static Order reconstitute(OrderId id, UUID customerId, List<OrderItem> items,
                                      OrderStatus status, Instant createdAt, Instant updatedAt) {
        return new Order(id, customerId, items, status, createdAt, updatedAt);
    }

    public void markStockReserved() {
        transitionTo(OrderStatus.STOCK_RESERVED);
    }

    public void markStockRejected() {
        transitionTo(OrderStatus.STOCK_REJECTED);
    }

    public void confirmPayment() {
        transitionTo(OrderStatus.PAYMENT_CONFIRMED);
    }

    public void cancel() {
        if (!status.isCancellable()) {
            throw new InvalidOrderStateTransitionException(status, OrderStatus.CANCELLED);
        }
        transitionTo(OrderStatus.CANCELLED);
    }

    public void markDelivered() {
        transitionTo(OrderStatus.DELIVERED);
    }

    private void transitionTo(OrderStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new InvalidOrderStateTransitionException(status, next);
        }
        this.status = next;
        this.updatedAt = Instant.now();
    }

    public Money totalAmount() {
        String currency = items.get(0).unitPrice().currency();
        Money total = Money.zero(currency);
        for (OrderItem item : items) {
            total = total.add(item.subtotal());
        }
        return total;
    }

    public OrderId id() {
        return id;
    }

    public UUID customerId() {
        return customerId;
    }

    /** Returns an unmodifiable view — callers cannot bypass the aggregate to mutate items directly. */
    public List<OrderItem> items() {
        return Collections.unmodifiableList(items);
    }

    public OrderStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Order other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
