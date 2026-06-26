package com.blinkitclone.orderservice.application.usecase;

import com.blinkitclone.orderservice.application.port.in.PlaceOrderUseCase.PlaceOrderCommand;
import com.blinkitclone.orderservice.application.port.in.PlaceOrderUseCase.PlaceOrderCommand.OrderItemCommand;
import com.blinkitclone.orderservice.application.port.in.PlaceOrderUseCase.PlaceOrderResult;
import com.blinkitclone.orderservice.application.port.out.OrderEventPublisher;
import com.blinkitclone.orderservice.application.port.out.OrderRepository;
import com.blinkitclone.orderservice.domain.model.Order;
import com.blinkitclone.orderservice.domain.model.OrderId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests PlaceOrderService against an in-memory fake of the OrderRepository
 * port — not Mockito, not @DataJpaTest, not a real database. This is the
 * direct payoff of the Dependency Inversion Principle applied via ports:
 * the application layer only knows about the OrderRepository *interface*,
 * so any implementation — a fake here, JPA in production — satisfies it.
 * These tests run in milliseconds and verify orchestration logic in
 * isolation from persistence concerns.
 */
class PlaceOrderServiceTest {

    private final InMemoryOrderRepository repository = new InMemoryOrderRepository();
    private final RecordingOrderEventPublisher eventPublisher = new RecordingOrderEventPublisher();
    private final PlaceOrderService service = new PlaceOrderService(repository, eventPublisher);

    @Test
    void placingAnOrderPersistsItAndReturnsComputedTotal() {
        PlaceOrderCommand command = new PlaceOrderCommand(
                UUID.randomUUID(),
                List.of(
                        new OrderItemCommand(UUID.randomUUID(), "Milk 1L", 2, new BigDecimal("55.00")),
                        new OrderItemCommand(UUID.randomUUID(), "Bread", 1, new BigDecimal("40.00"))
                ));

        PlaceOrderResult result = service.placeOrder(command);

        assertThat(result.status()).isEqualTo("CREATED");
        assertThat(result.totalAmount()).isEqualTo(new BigDecimal("150.00"));
        assertThat(result.currency()).isEqualTo("INR");
        assertThat(repository.findById(OrderId.of(result.orderId()))).isPresent();
    }

    @Test
    void placingAnOrderPublishesExactlyOneOrderCreatedEvent() {
        PlaceOrderCommand command = new PlaceOrderCommand(
                UUID.randomUUID(),
                List.of(new OrderItemCommand(UUID.randomUUID(), "Milk 1L", 1, new BigDecimal("55.00"))));

        PlaceOrderResult result = service.placeOrder(command);

        assertThat(eventPublisher.publishedOrders).hasSize(1);
        assertThat(eventPublisher.publishedOrders.get(0).id().value()).isEqualTo(result.orderId());
    }

    /** A minimal fake satisfying the OrderEventPublisher port, recording calls instead of touching RabbitMQ. */
    private static final class RecordingOrderEventPublisher implements OrderEventPublisher {
        private final List<Order> publishedOrders = new ArrayList<>();

        @Override
        public void publishOrderCreated(Order order) {
            publishedOrders.add(order);
        }
    }

    /** A minimal fake satisfying the OrderRepository port, backed by a Map instead of a database. */
    private static final class InMemoryOrderRepository implements OrderRepository {
        private final Map<OrderId, Order> store = new HashMap<>();

        @Override
        public Order save(Order order) {
            store.put(order.id(), order);
            return order;
        }

        @Override
        public Optional<Order> findById(OrderId id) {
            return Optional.ofNullable(store.get(id));
        }
    }
}
