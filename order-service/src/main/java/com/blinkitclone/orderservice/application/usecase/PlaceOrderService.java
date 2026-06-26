package com.blinkitclone.orderservice.application.usecase;

import com.blinkitclone.orderservice.application.port.in.PlaceOrderUseCase;
import com.blinkitclone.orderservice.application.port.out.OrderEventPublisher;
import com.blinkitclone.orderservice.application.port.out.OrderRepository;
import com.blinkitclone.orderservice.domain.model.Money;
import com.blinkitclone.orderservice.domain.model.Order;
import com.blinkitclone.orderservice.domain.model.OrderItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The use case implementation. This class orchestrates a single business
 * transaction: build domain objects, ask the aggregate to enforce its own
 * invariants, persist via the output port, return a result.
 *
 * <p>Deliberately thin — it contains no business *rules* (those live in
 * Order/OrderItem/Money), only *coordination*. This split is what the Single
 * Responsibility Principle looks like in practice here: the domain model's
 * reason to change is "the business rules changed"; this service's reason to
 * change is "the steps to fulfil this use case changed" (e.g. later we'll add
 * "publish an OrderCreated event" as a new step, without touching Order).
 *
 * <p>The only Spring dependency is `@Service` and `@Transactional` — applied
 * here at the application layer, which is the conventional and correct place
 * for transaction boundaries (one use case = one transaction).
 */
@Service
public class PlaceOrderService implements PlaceOrderUseCase {

    private static final String DEFAULT_CURRENCY = "INR";

    private final OrderRepository orderRepository;
    private final OrderEventPublisher orderEventPublisher;

    public PlaceOrderService(OrderRepository orderRepository, OrderEventPublisher orderEventPublisher) {
        this.orderRepository = orderRepository;
        this.orderEventPublisher = orderEventPublisher;
    }

    @Override
    @Transactional
    public PlaceOrderResult placeOrder(PlaceOrderCommand command) {
        List<OrderItem> items = command.items().stream()
                .map(this::toDomainItem)
                .toList();

        Order order = Order.place(command.customerId(), items);
        Order saved = orderRepository.save(order);
        orderEventPublisher.publishOrderCreated(saved);

        Money total = saved.totalAmount();
        return new PlaceOrderResult(
                saved.id().value(),
                saved.status().name(),
                total.amount(),
                total.currency());
    }

    private OrderItem toDomainItem(PlaceOrderCommand.OrderItemCommand itemCommand) {
        Money unitPrice = Money.of(itemCommand.unitPrice(), DEFAULT_CURRENCY);
        return OrderItem.of(itemCommand.productId(), itemCommand.productName(), itemCommand.quantity(), unitPrice);
    }
}
