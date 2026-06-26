package com.blinkitclone.inventoryservice.application.usecase;

import com.blinkitclone.inventoryservice.application.port.in.SeedStockUseCase;
import com.blinkitclone.inventoryservice.application.port.out.StockRepository;
import com.blinkitclone.inventoryservice.domain.model.Stock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeedStockService implements SeedStockUseCase {

    private final StockRepository stockRepository;

    public SeedStockService(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    @Override
    @Transactional
    public void seedStock(SeedStockCommand command) {
        Stock stock = Stock.initialize(command.productId(), command.productName(), command.initialQuantity());
        stockRepository.save(stock);
    }
}
