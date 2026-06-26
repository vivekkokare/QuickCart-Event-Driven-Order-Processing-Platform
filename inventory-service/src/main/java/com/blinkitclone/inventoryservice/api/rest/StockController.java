package com.blinkitclone.inventoryservice.api.rest;

import com.blinkitclone.inventoryservice.api.dto.SeedStockRequest;
import com.blinkitclone.inventoryservice.api.dto.StockResponse;
import com.blinkitclone.inventoryservice.application.port.in.SeedStockUseCase;
import com.blinkitclone.inventoryservice.application.port.in.SeedStockUseCase.SeedStockCommand;
import com.blinkitclone.inventoryservice.application.port.out.StockRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Note getStock() depends directly on the StockRepository output port rather
 * than going through a dedicated query use case - a deliberate, minor
 * deviation for a trivial read with no business logic. Writes (seedStock)
 * still go through a use case because seeding enforces domain invariants
 * (Stock.initialize's validation). If this read ever grows logic beyond
 * "fetch and map," it should get its own use case like everything else.
 */
@RestController
@RequestMapping("/api/v1/stock")
public class StockController {

    private final SeedStockUseCase seedStockUseCase;
    private final StockRepository stockRepository;

    public StockController(SeedStockUseCase seedStockUseCase, StockRepository stockRepository) {
        this.seedStockUseCase = seedStockUseCase;
        this.stockRepository = stockRepository;
    }

    @PostMapping
    public ResponseEntity<Void> seedStock(@Valid @RequestBody SeedStockRequest request) {
        seedStockUseCase.seedStock(new SeedStockCommand(request.productId(), request.productName(), request.initialQuantity()));
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/{productId}")
    public ResponseEntity<StockResponse> getStock(@PathVariable UUID productId) {
        return stockRepository.findByProductId(productId)
                .map(stock -> new StockResponse(stock.productId(), stock.productName(), stock.availableQuantity()))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
