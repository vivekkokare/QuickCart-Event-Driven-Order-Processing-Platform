package com.blinkitclone.orderservice.application.port.out;

import com.blinkitclone.orderservice.domain.model.Order;
import com.blinkitclone.orderservice.domain.model.OrderId;

import java.util.Optional;

/**
 * An "output port" — the application layer's contract for persistence,
 * expressed purely in terms of the domain model (Order in, Order out, no
 * JPA entity, no SQL). The application layer depends on this interface, not
 * on any concrete database technology. The infrastructure layer provides the
 * implementation (see infrastructure.persistence.adapter.OrderRepositoryAdapter)
 * and is plugged in via Spring's dependency injection.
 *
 * <p>This is the Dependency Inversion Principle in action: the high-level
 * policy (use cases) defines the interface it needs; the low-level detail
 * (Postgres via JPA) depends on and conforms to that interface — never the
 * other way around. It is also what makes the use case unit-testable with a
 * plain in-memory fake, no database required.
 */
public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findById(OrderId id);
}
