package com.blinkitclone.orderservice.infrastructure.persistence.repository;

import com.blinkitclone.orderservice.infrastructure.persistence.entity.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Spring Data JPA repository — the actual SQL-generating interface. Note this
 * is a *different* interface from application.port.out.OrderRepository: this
 * one is infrastructure-only, works with OrderEntity, and is consumed
 * exclusively by OrderRepositoryAdapter, never directly by the application
 * layer. Keeping Spring Data out of the application layer means swapping
 * Postgres/JPA for something else later only touches this package.
 */
public interface OrderJpaRepository extends JpaRepository<OrderEntity, UUID> {
}
