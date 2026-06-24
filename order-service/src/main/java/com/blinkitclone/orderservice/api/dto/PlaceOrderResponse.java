package com.blinkitclone.orderservice.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** The REST response shape returned to the client after placing an order. */
public record PlaceOrderResponse(UUID orderId, String status, BigDecimal totalAmount, String currency) {
}
