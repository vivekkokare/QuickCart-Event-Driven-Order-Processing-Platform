package com.blinkitclone.inventoryservice.domain.model;

import com.blinkitclone.inventoryservice.domain.exception.InsufficientStockException;

import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate root representing the stock level for a single product. This is
 * the inventory bounded context's own model of a "product" — note it knows
 * nothing about price, description, or anything order-service's domain
 * cares about. Two services are allowed, even expected, to model the same
 * real-world thing (a product) differently, because each only needs the
 * facts relevant to its own responsibility. That divergence is intentional
 * and is one of the core ideas of bounded contexts in DDD.
 */
public final class Stock {

    private final UUID productId;
    private String productName;
    private int availableQuantity;

    private Stock(UUID productId, String productName, int availableQuantity) {
        this.productId = productId;
        this.productName = productName;
        this.availableQuantity = availableQuantity;
    }

    public static Stock initialize(UUID productId, String productName, int initialQuantity) {
        Objects.requireNonNull(productId, "productId must not be null");
        Objects.requireNonNull(productName, "productName must not be null");
        if (initialQuantity < 0) {
            throw new IllegalArgumentException("initialQuantity cannot be negative: " + initialQuantity);
        }
        return new Stock(productId, productName, initialQuantity);
    }

    public static Stock reconstitute(UUID productId, String productName, int availableQuantity) {
        return new Stock(productId, productName, availableQuantity);
    }

    /**
     * Reserves the given quantity, decrementing availability. Throws if there
     * isn't enough stock — the caller (the use case) is responsible for
     * deciding what "rejected" means for the order (e.g. publish
     * StockRejected rather than letting the exception propagate to a queue
     * consumer, where an uncaught exception would trigger a redelivery loop).
     */
    public void reserve(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity to reserve must be positive: " + quantity);
        }
        if (quantity > availableQuantity) {
            throw new InsufficientStockException(productId, quantity, availableQuantity);
        }
        this.availableQuantity -= quantity;
    }

    public UUID productId() {
        return productId;
    }

    public String productName() {
        return productName;
    }

    public int availableQuantity() {
        return availableQuantity;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Stock other)) return false;
        return productId.equals(other.productId);
    }

    @Override
    public int hashCode() {
        return productId.hashCode();
    }
}
