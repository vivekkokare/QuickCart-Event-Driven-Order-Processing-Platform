package com.blinkitclone.orderservice.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Money as a value object instead of a raw BigDecimal/double. Money has rules
 * (never negative for a price, currency must match before arithmetic, fixed
 * rounding) that would otherwise be scattered and duplicated across the codebase.
 * Centralizing them here means there is exactly one place a "negative price" bug
 * or a "compared two different currencies" bug could ever occur.
 */
public final class Money {

    private static final int SCALE = 2;

    private final BigDecimal amount;
    private final String currency;

    private Money(BigDecimal amount, String currency) {
        this.amount = amount.setScale(SCALE, RoundingMode.HALF_UP);
        this.currency = currency;
    }

    public static Money of(BigDecimal amount, String currency) {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Money amount cannot be negative: " + amount);
        }
        return new Money(amount, currency);
    }

    public static Money zero(String currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public Money multiply(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("quantity cannot be negative: " + quantity);
        }
        return new Money(this.amount.multiply(BigDecimal.valueOf(quantity)), this.currency);
    }

    private void requireSameCurrency(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Cannot operate on Money with different currencies: " + this.currency + " vs " + other.currency);
        }
    }

    public BigDecimal amount() {
        return amount;
    }

    public String currency() {
        return currency;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money other)) return false;
        return amount.equals(other.amount) && currency.equals(other.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount, currency);
    }

    @Override
    public String toString() {
        return amount + " " + currency;
    }
}
