package com.blinkitclone.orderservice.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void rejectsNegativeAmounts() {
        assertThatThrownBy(() -> Money.of(new BigDecimal("-1.00"), "INR"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void roundsToTwoDecimalPlaces() {
        Money money = Money.of(new BigDecimal("10.005"), "INR");

        assertThat(money.amount()).isEqualTo(new BigDecimal("10.01"));
    }

    @Test
    void addingDifferentCurrenciesThrows() {
        Money inr = Money.of(new BigDecimal("10.00"), "INR");
        Money usd = Money.of(new BigDecimal("10.00"), "USD");

        assertThatThrownBy(() -> inr.add(usd)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void multiplyByQuantityScalesAmount() {
        Money unitPrice = Money.of(new BigDecimal("25.50"), "INR");

        Money result = unitPrice.multiply(3);

        assertThat(result.amount()).isEqualTo(new BigDecimal("76.50"));
    }

    @Test
    void multiplyByNegativeQuantityThrows() {
        Money unitPrice = Money.of(new BigDecimal("25.50"), "INR");

        assertThatThrownBy(() -> unitPrice.multiply(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
