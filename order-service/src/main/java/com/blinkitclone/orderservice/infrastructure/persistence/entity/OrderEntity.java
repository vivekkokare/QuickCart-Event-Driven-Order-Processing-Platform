package com.blinkitclone.orderservice.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA entity for the orders table. Note this lives in
 * infrastructure.persistence.entity, not domain.model — it is a *persistence*
 * concept (table row), not a *domain* concept (aggregate with behaviour). The
 * mapping between the two happens explicitly in OrderRepositoryAdapter, which
 * keeps the translation visible and testable instead of relying on JPA to
 * directly manage domain objects.
 */
@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
public class OrderEntity {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<OrderItemEntity> items = new ArrayList<>();
}
