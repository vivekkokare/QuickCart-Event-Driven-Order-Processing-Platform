package com.blinkitclone.orderservice.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Identity value object for an Order. Wrapping a raw UUID prevents accidentally
 * passing a CustomerId or ProductId where an OrderId is expected — the compiler
 * catches it instead of a runtime bug.
 */
public final class OrderId {

    private final UUID value;

    private OrderId(UUID value) {
        this.value = value;
    }

    public static OrderId newId() {
        return new OrderId(UUID.randomUUID());
    }

    public static OrderId of(UUID value) {
        Objects.requireNonNull(value, "OrderId value must not be null");
        return new OrderId(value);
    }

    public static OrderId of(String value) {
        return of(UUID.fromString(value));
    }

    public UUID value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OrderId other)) return false;
        return value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
