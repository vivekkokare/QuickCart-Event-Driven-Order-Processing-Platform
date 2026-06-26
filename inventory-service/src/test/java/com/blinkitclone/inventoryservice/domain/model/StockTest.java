package com.blinkitclone.inventoryservice.domain.model;

import com.blinkitclone.inventoryservice.domain.exception.InsufficientStockException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockTest {

    @Test
    void reservingWithinAvailableQuantitySucceeds() {
        Stock stock = Stock.initialize(UUID.randomUUID(), "Milk 1L", 10);

        stock.reserve(4);

        assertThat(stock.availableQuantity()).isEqualTo(6);
    }

    @Test
    void reservingMoreThanAvailableThrows() {
        Stock stock = Stock.initialize(UUID.randomUUID(), "Milk 1L", 2);

        assertThatThrownBy(() -> stock.reserve(3)).isInstanceOf(InsufficientStockException.class);
        assertThat(stock.availableQuantity()).isEqualTo(2);
    }

    @Test
    void reservingNonPositiveQuantityThrows() {
        Stock stock = Stock.initialize(UUID.randomUUID(), "Milk 1L", 10);

        assertThatThrownBy(() -> stock.reserve(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void initializingWithNegativeQuantityThrows() {
        assertThatThrownBy(() -> Stock.initialize(UUID.randomUUID(), "Milk 1L", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
