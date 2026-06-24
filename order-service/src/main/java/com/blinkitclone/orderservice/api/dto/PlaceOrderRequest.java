package com.blinkitclone.orderservice.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * The REST contract for placing an order. This is deliberately a separate
 * type from PlaceOrderUseCase.PlaceOrderCommand: this DTO's job is to validate
 * and describe the wire format (JSON shape, Bean Validation annotations,
 * versioning concerns); the Command's job is to describe what the application
 * layer needs. They happen to look alike today but are allowed to diverge.
 */
public record PlaceOrderRequest(
        @NotNull UUID customerId,
        @NotEmpty List<@Valid OrderItemRequest> items) {

    public record OrderItemRequest(
            @NotNull UUID productId,
            @NotBlank String productName,
            @Positive int quantity,
            @NotNull @PositiveOrZero BigDecimal unitPrice) {
    }
}
