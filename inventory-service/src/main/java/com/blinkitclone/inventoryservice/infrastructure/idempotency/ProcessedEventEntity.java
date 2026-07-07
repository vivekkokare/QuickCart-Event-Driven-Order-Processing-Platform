package com.blinkitclone.inventoryservice.infrastructure.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per successfully processed event id. Existence of a row is the
 * idempotency check: if eventId is already here, the listener has already
 * reserved stock for it and must not do so again. The PK on event_id is
 * what makes a concurrent duplicate-processing race fail safely (a unique
 * constraint violation) rather than silently double-reserving.
 */
@Entity
@Table(name = "processed_events")
@Getter
@Setter
@NoArgsConstructor
public class ProcessedEventEntity {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;
}
