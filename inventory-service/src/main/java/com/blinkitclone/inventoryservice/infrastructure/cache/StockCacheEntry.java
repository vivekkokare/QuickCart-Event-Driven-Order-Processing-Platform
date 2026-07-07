package com.blinkitclone.inventoryservice.infrastructure.cache;

import com.blinkitclone.inventoryservice.domain.model.Stock;

import java.util.UUID;

/**
 * A serializable snapshot of a Stock aggregate, used exclusively as the
 * Redis cache value. Keeping this in the infrastructure layer instead of
 * adding Jackson annotations to the domain model respects the Clean
 * Architecture rule that domain objects have no infrastructure concerns.
 *
 * <p>Being a record with all-primitive/UUID fields makes Jackson
 * serialization/deserialization work with zero annotation noise.
 */
public record StockCacheEntry(UUID productId, String productName, int availableQuantity) {

    public static StockCacheEntry from(Stock stock) {
        return new StockCacheEntry(stock.productId(), stock.productName(), stock.availableQuantity());
    }

    public Stock toDomain() {
        return Stock.reconstitute(productId, productName, availableQuantity);
    }
}
