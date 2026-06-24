package com.blinkitclone.orderservice.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * A single line item within an Order. It is a value object, not an entity:
 * two OrderItems with the same productId, quantity, and price are
 * interchangeable, and an item has no identity or lifecycle independent of
 * the Order that owns it.
 */
public final class OrderItem {

    private final UUID productId;
    private final String productName;
    private final int quantity;
    private final Money unitPrice;

    private OrderItem(UUID productId, String productName, int quantity, Money unitPrice) {
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public static OrderItem of(UUID productId, String productName, int quantity, Money unitPrice) {
        Objects.requireNonNull(productId, "productId must not be null");
        Objects.requireNonNull(productName, "productName must not be null");
        Objects.requireNonNull(unitPrice, "unitPrice must not be null");
        if (productName.isBlank()) {
            throw new IllegalArgumentException("productName must not be blank");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive, got: " + quantity);
        }
        return new OrderItem(productId, productName, quantity, unitPrice);
    }

    public Money subtotal() {
        return unitPrice.multiply(quantity);
    }

    public UUID productId() {
        return productId;
    }

    public String productName() {
        return productName;
    }

    public int quantity() {
        return quantity;
    }

    public Money unitPrice() {
        return unitPrice;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OrderItem other)) return false;
        return quantity == other.quantity
                && productId.equals(other.productId)
                && productName.equals(other.productName)
                && unitPrice.equals(other.unitPrice);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productId, productName, quantity, unitPrice);
    }
}
