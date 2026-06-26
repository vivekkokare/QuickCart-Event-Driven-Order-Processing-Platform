package com.blinkitclone.inventoryservice.infrastructure.persistence.repository;

import com.blinkitclone.inventoryservice.infrastructure.persistence.entity.StockEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StockJpaRepository extends JpaRepository<StockEntity, UUID> {

    List<StockEntity> findByProductIdIn(List<UUID> productIds);
}
