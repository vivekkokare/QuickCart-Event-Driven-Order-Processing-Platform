package com.blinkitclone.inventoryservice.infrastructure.idempotency;

import com.blinkitclone.inventoryservice.application.port.out.ProcessedEventStore;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class JpaProcessedEventStore implements ProcessedEventStore {

    private final ProcessedEventJpaRepository repository;

    public JpaProcessedEventStore(ProcessedEventJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean alreadyProcessed(UUID eventId) {
        return repository.existsById(eventId);
    }

    @Override
    public void markProcessed(UUID eventId) {
        ProcessedEventEntity entity = new ProcessedEventEntity();
        entity.setEventId(eventId);
        entity.setProcessedAt(Instant.now());
        repository.save(entity);
    }
}
