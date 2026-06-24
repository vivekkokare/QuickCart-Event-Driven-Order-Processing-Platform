package com.blinkitclone.orderservice.domain.exception;

/** Thrown when an Order is created or would end up with zero line items. */
public class EmptyOrderException extends RuntimeException {

    public EmptyOrderException() {
        super("Order must contain at least one item");
    }
}
