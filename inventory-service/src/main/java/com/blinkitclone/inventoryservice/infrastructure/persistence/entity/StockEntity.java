package com.blinkitclone.inventoryservice.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "stock")
@Getter
@Setter
@NoArgsConstructor
public class StockEntity {

    @Id
    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "available_quantity", nullable = false)
    private int availableQuantity;

    /**
     * Optimistic locking column. Flagged here now (not yet relied upon by the
     * application code) as the documented fix for the concurrent-reservation
     * race noted in ReserveStockService — a concurrent update bumps this
     * version and causes a conflicting transaction to fail fast instead of
     * silently overwriting another reservation.
     */
    @Version
    @Column(name = "version")
    private Long version;
}
