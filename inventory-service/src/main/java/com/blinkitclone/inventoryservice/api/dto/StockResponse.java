package com.blinkitclone.inventoryservice.api.dto;

import java.util.UUID;

public record StockResponse(UUID productId, String productName, int availableQuantity) {
}
