package com.blinkitclone.orderservice.domain.model;

import com.blinkitclone.orderservice.domain.exception.EmptyOrderException;
import com.blinkitclone.orderservice.domain.exception.InvalidOrderStateTransitionException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure unit tests for the Order aggregate. No Spring context, no database,
 * no mocks — just plain objects and assertions. This is the payoff of keeping
 * the domain layer framework-free: these tests run in milliseconds and pin
 * down business rules independently of any infrastructure decision.
 */
class OrderTest {

    private static OrderItem sampleItem(int quantity, String price) {
        return OrderItem.of(UUID.randomUUID(), "Milk 1L", quantity, Money.of(new BigDecimal(price), "INR"));
    }

    @Test
    void placingAnOrderStartsInCreatedStatus() {
        Order order = Order.place(UUID.randomUUID(), List.of(sampleItem(1, "55.00")));

        assertThat(order.status()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.id()).isNotNull();
    }

    @Test
    void placingAnOrderWithNoItemsThrows() {
        assertThatThrownBy(() -> Order.place(UUID.randomUUID(), List.of()))
                .isInstanceOf(EmptyOrderException.class);
    }

    @Test
    void totalAmountSumsAllItemSubtotals() {
        Order order = Order.place(UUID.randomUUID(), List.of(
                sampleItem(2, "55.00"),   // 110.00
                sampleItem(1, "40.00")    // 40.00
        ));

        Money total = order.totalAmount();

        assertThat(total.amount()).isEqualTo(new BigDecimal("150.00"));
        assertThat(total.currency()).isEqualTo("INR");
    }

    @Test
    void legalTransitionSequenceSucceeds() {
        Order order = Order.place(UUID.randomUUID(), List.of(sampleItem(1, "55.00")));

        order.markStockReserved();
        order.confirmPayment();
        order.markDelivered();

        assertThat(order.status()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void cannotConfirmPaymentBeforeStockIsReserved() {
        Order order = Order.place(UUID.randomUUID(), List.of(sampleItem(1, "55.00")));

        assertThatThrownBy(order::confirmPayment)
                .isInstanceOf(InvalidOrderStateTransitionException.class);
    }

    @Test
    void cannotCancelAfterDelivery() {
        Order order = Order.place(UUID.randomUUID(), List.of(sampleItem(1, "55.00")));
        order.markStockReserved();
        order.confirmPayment();
        order.markDelivered();

        assertThatThrownBy(order::cancel)
                .isInstanceOf(InvalidOrderStateTransitionException.class);
    }

    @Test
    void canCancelWhileStockIsReserved() {
        Order order = Order.place(UUID.randomUUID(), List.of(sampleItem(1, "55.00")));
        order.markStockReserved();

        order.cancel();

        assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void itemsListIsUnmodifiableFromOutside() {
        Order order = Order.place(UUID.randomUUID(), List.of(sampleItem(1, "55.00")));

        assertThatThrownBy(() -> order.items().add(sampleItem(1, "10.00")))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
