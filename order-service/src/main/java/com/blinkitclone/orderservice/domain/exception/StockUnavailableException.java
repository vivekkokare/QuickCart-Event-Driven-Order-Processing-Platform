package com.blinkitclone.orderservice.domain.exception;

import java.util.UUID;

public class StockUnavailableException extends RuntimeException {

    public StockUnavailableException(UUID productId, int requested) {
        super(String.format(
                "Insufficient stock for product %s: requested %d units but pre-check reports unavailable",
                productId, requested));
    }
}
