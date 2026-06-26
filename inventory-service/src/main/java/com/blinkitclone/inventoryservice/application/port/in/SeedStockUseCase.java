package com.blinkitclone.inventoryservice.application.port.in;

import java.util.UUID;

/**
 * A deliberately simple "admin" use case for seeding initial stock levels.
 * In a real system this would likely be driven by a ProductCreated event
 * from a separate catalog service rather than a direct REST call - modeled
 * as a REST endpoint here only so we have a way to set up test data without
 * touching the database by hand.
 */
public interface SeedStockUseCase {

    void seedStock(SeedStockCommand command);

    record SeedStockCommand(UUID productId, String productName, int initialQuantity) {
    }
}
