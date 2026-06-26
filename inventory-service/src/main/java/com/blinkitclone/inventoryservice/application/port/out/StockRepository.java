package com.blinkitclone.inventoryservice.application.port.out;

import com.blinkitclone.inventoryservice.domain.model.Stock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockRepository {

    Stock save(Stock stock);

    Optional<Stock> findByProductId(UUID productId);

    List<Stock> findAllByProductIdIn(List<UUID> productIds);

    List<Stock> saveAll(List<Stock> stocks);
}
