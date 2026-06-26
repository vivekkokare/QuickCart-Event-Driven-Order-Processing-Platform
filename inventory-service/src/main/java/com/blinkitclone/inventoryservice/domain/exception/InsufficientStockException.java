package com.blinkitclone.inventoryservice.domain.exception;

import java.util.UUID;

/** Thrown when a reservation is requested for more units than are currently available. */
public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(UUID productId, int requested, int available) {
        super("Cannot reserve " + requested + " units of product " + productId
                + ": only " + available + " available");
    }
}
