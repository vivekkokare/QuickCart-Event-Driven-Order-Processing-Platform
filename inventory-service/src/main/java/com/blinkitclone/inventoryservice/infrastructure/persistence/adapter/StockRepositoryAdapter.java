package com.blinkitclone.inventoryservice.infrastructure.persistence.adapter;

import com.blinkitclone.inventoryservice.application.port.out.StockRepository;
import com.blinkitclone.inventoryservice.domain.model.Stock;
import com.blinkitclone.inventoryservice.infrastructure.persistence.entity.StockEntity;
import com.blinkitclone.inventoryservice.infrastructure.persistence.repository.StockJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class StockRepositoryAdapter implements StockRepository {

    private final StockJpaRepository jpaRepository;

    public StockRepositoryAdapter(StockJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Stock save(Stock stock) {
        StockEntity saved = jpaRepository.save(toEntity(stock, null));
        return toDomain(saved);
    }

    @Override
    public Optional<Stock> findByProductId(UUID productId) {
        return jpaRepository.findById(productId).map(this::toDomain);
    }

    @Override
    public List<Stock> findAllByProductIdIn(List<UUID> productIds) {
        return jpaRepository.findByProductIdIn(productIds).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Stock> saveAll(List<Stock> stocks) {
        List<UUID> productIds = stocks.stream().map(Stock::productId).toList();
        var existingByProductId = jpaRepository.findByProductIdIn(productIds).stream()
                .collect(java.util.stream.Collectors.toMap(StockEntity::getProductId, e -> e));

        List<StockEntity> toPersist = stocks.stream()
                .map(stock -> toEntity(stock, existingByProductId.get(stock.productId())))
                .toList();

        return jpaRepository.saveAll(toPersist).stream().map(this::toDomain).toList();
    }

    private StockEntity toEntity(Stock stock, StockEntity existing) {
        StockEntity entity = existing != null ? existing : new StockEntity();
        entity.setProductId(stock.productId());
        entity.setProductName(stock.productName());
        entity.setAvailableQuantity(stock.availableQuantity());
        return entity;
    }

    private Stock toDomain(StockEntity entity) {
        return Stock.reconstitute(entity.getProductId(), entity.getProductName(), entity.getAvailableQuantity());
    }
}
