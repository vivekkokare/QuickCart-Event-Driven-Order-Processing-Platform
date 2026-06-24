package com.blinkitclone.orderservice.infrastructure.persistence.adapter;

import com.blinkitclone.orderservice.application.port.out.OrderRepository;
import com.blinkitclone.orderservice.domain.model.*;
import com.blinkitclone.orderservice.infrastructure.persistence.entity.OrderEntity;
import com.blinkitclone.orderservice.infrastructure.persistence.entity.OrderItemEntity;
import com.blinkitclone.orderservice.infrastructure.persistence.repository.OrderJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Implements the application layer's OrderRepository port using Spring Data
 * JPA. This is the "adapter" in ports-and-adapters: it adapts the
 * Spring-Data/JPA world to the shape the application layer asked for. All
 * domain-to-entity and entity-to-domain mapping is explicit and lives only
 * here — nowhere else in the codebase needs to know both models exist.
 */
@Component
public class OrderRepositoryAdapter implements OrderRepository {

    private final OrderJpaRepository jpaRepository;

    public OrderRepositoryAdapter(OrderJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Order save(Order order) {
        OrderEntity entity = toEntity(order);
        OrderEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Order> findById(OrderId id) {
        return jpaRepository.findById(id.value()).map(this::toDomain);
    }

    private OrderEntity toEntity(Order order) {
        OrderEntity entity = new OrderEntity();
        entity.setId(order.id().value());
        entity.setCustomerId(order.customerId());
        entity.setStatus(order.status().name());
        entity.setCreatedAt(order.createdAt());
        entity.setUpdatedAt(order.updatedAt());

        List<OrderItemEntity> itemEntities = order.items().stream().map(item -> {
            OrderItemEntity itemEntity = new OrderItemEntity();
            itemEntity.setOrder(entity);
            itemEntity.setProductId(item.productId());
            itemEntity.setProductName(item.productName());
            itemEntity.setQuantity(item.quantity());
            itemEntity.setUnitPrice(item.unitPrice().amount());
            itemEntity.setCurrency(item.unitPrice().currency());
            return itemEntity;
        }).toList();
        entity.setItems(itemEntities);

        return entity;
    }

    private Order toDomain(OrderEntity entity) {
        List<OrderItem> items = entity.getItems().stream()
                .map(itemEntity -> OrderItem.of(
                        itemEntity.getProductId(),
                        itemEntity.getProductName(),
                        itemEntity.getQuantity(),
                        Money.of(itemEntity.getUnitPrice(), itemEntity.getCurrency())))
                .toList();

        return Order.reconstitute(
                OrderId.of(entity.getId()),
                entity.getCustomerId(),
                items,
                OrderStatus.valueOf(entity.getStatus()),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
