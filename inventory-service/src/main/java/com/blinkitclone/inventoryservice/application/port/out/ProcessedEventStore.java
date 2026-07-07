package com.blinkitclone.inventoryservice.application.port.out;

import java.util.UUID;

/**
 * Output port for the idempotency check. Deliberately separate from
 * StockRepository even though both are persistence concerns - this one has
 * nothing to do with the Stock domain model, it's purely a "have I seen this
 * message before" guard, so it gets its own narrow port rather than being
 * bolted onto an unrelated repository interface.
 */
public interface ProcessedEventStore {

    boolean alreadyProcessed(UUID eventId);

    void markProcessed(UUID eventId);
}
