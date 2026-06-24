package com.blinkitclone.orderservice.domain.exception;

import com.blinkitclone.orderservice.domain.model.OrderStatus;

/**
 * Thrown when code attempts to move an Order to a status that is not a legal
 * transition from its current status. This is a domain rule violation, not a
 * technical error, so it lives in the domain layer and carries no framework
 * dependency (no @ResponseStatus, no HTTP code) — the API layer decides how
 * to translate it into a response.
 */
public class InvalidOrderStateTransitionException extends RuntimeException {

    public InvalidOrderStateTransitionException(OrderStatus from, OrderStatus to) {
        super("Cannot transition order from " + from + " to " + to);
    }
}
