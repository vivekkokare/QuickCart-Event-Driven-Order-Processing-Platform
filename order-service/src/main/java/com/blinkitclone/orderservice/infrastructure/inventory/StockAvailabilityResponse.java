package com.blinkitclone.orderservice.infrastructure.inventory;

import java.util.UUID;

/**
 * Local DTO matching inventory-service's GET /api/v1/stock/{productId} response.
 * Lives in infrastructure (not application/domain) because it reflects the wire
 * format of an external service — that's an infrastructure concern. The domain
 * never sees this type; InventoryServiceClient converts it to a plain boolean.
 */
record StockAvailabilityResponse(UUID productId, String productName, int availableQuantity) {
}
