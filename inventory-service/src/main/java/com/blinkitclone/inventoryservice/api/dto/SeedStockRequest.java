package com.blinkitclone.inventoryservice.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record SeedStockRequest(
        @NotNull UUID productId,
        @NotBlank String productName,
        @PositiveOrZero int initialQuantity) {
}
