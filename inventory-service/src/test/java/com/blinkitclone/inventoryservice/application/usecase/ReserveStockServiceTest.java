package com.blinkitclone.inventoryservice.application.usecase;

import com.blinkitclone.inventoryservice.application.port.in.ReserveStockUseCase.ReserveStockCommand;
import com.blinkitclone.inventoryservice.application.port.in.ReserveStockUseCase.ReserveStockCommand.ReservationItem;
import com.blinkitclone.inventoryservice.application.port.in.ReserveStockUseCase.ReservationResult;
import com.blinkitclone.inventoryservice.application.port.out.ProcessedEventStore;
import com.blinkitclone.inventoryservice.application.port.out.StockRepository;
import com.blinkitclone.inventoryservice.domain.model.Stock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the all-or-nothing reservation logic against an in-memory fake of
 * StockRepository - same pattern as PlaceOrderServiceTest in order-service.
 * The interesting case here is the "atomic across multiple items" rule:
 * if any single item lacks sufficient stock, no item in the order should
 * be decremented.
 */
class ReserveStockServiceTest {

    private final InMemoryStockRepository repository = new InMemoryStockRepository();
    private final InMemoryProcessedEventStore processedEventStore = new InMemoryProcessedEventStore();
    private final ReserveStockService service = new ReserveStockService(repository, processedEventStore);

    private UUID milkId;
    private UUID breadId;

    @BeforeEach
    void seedStock() {
        milkId = UUID.randomUUID();
        breadId = UUID.randomUUID();
        repository.save(Stock.initialize(milkId, "Milk 1L", 10));
        repository.save(Stock.initialize(breadId, "Bread", 2));
    }

    @Test
    void reservesStockWhenAllItemsHaveSufficientQuantity() {
        ReserveStockCommand command = new ReserveStockCommand(UUID.randomUUID(), UUID.randomUUID(), List.of(
                new ReservationItem(milkId, 3),
                new ReservationItem(breadId, 1)
        ));

        ReservationResult result = service.reserveStock(command);

        assertThat(result.reserved()).isTrue();
        assertThat(repository.findByProductId(milkId).orElseThrow().availableQuantity()).isEqualTo(7);
        assertThat(repository.findByProductId(breadId).orElseThrow().availableQuantity()).isEqualTo(1);
    }

    @Test
    void rejectsWithoutMutatingAnyStockWhenOneItemIsInsufficient() {
        ReserveStockCommand command = new ReserveStockCommand(UUID.randomUUID(), UUID.randomUUID(), List.of(
                new ReservationItem(milkId, 3),
                new ReservationItem(breadId, 5) // only 2 available
        ));

        ReservationResult result = service.reserveStock(command);

        assertThat(result.reserved()).isFalse();
        assertThat(repository.findByProductId(milkId).orElseThrow().availableQuantity()).isEqualTo(10);
        assertThat(repository.findByProductId(breadId).orElseThrow().availableQuantity()).isEqualTo(2);
    }

    @Test
    void rejectsForUnknownProduct() {
        ReserveStockCommand command = new ReserveStockCommand(UUID.randomUUID(), UUID.randomUUID(), List.of(
                new ReservationItem(UUID.randomUUID(), 1)
        ));

        ReservationResult result = service.reserveStock(command);

        assertThat(result.reserved()).isFalse();
        assertThat(result.reason()).contains("Unknown product");
    }

    @Test
    void redeliveredEventIsANoOpAndDoesNotDoubleDecrementStock() {
        UUID eventId = UUID.randomUUID();
        ReserveStockCommand command = new ReserveStockCommand(eventId, UUID.randomUUID(), List.of(
                new ReservationItem(milkId, 3)
        ));

        service.reserveStock(command);
        ReservationResult secondDelivery = service.reserveStock(command);

        assertThat(secondDelivery.reserved()).isTrue();
        assertThat(repository.findByProductId(milkId).orElseThrow().availableQuantity()).isEqualTo(7);
    }

    private static final class InMemoryStockRepository implements StockRepository {
        private final Map<UUID, Stock> store = new HashMap<>();

        @Override
        public Stock save(Stock stock) {
            store.put(stock.productId(), stock);
            return stock;
        }

        @Override
        public Optional<Stock> findByProductId(UUID productId) {
            return Optional.ofNullable(store.get(productId));
        }

        @Override
        public List<Stock> findAllByProductIdIn(List<UUID> productIds) {
            List<Stock> result = new ArrayList<>();
            for (UUID id : productIds) {
                Stock stock = store.get(id);
                if (stock != null) {
                    result.add(stock);
                }
            }
            return result;
        }

        @Override
        public List<Stock> saveAll(List<Stock> stocks) {
            stocks.forEach(this::save);
            return stocks;
        }
    }

    private static final class InMemoryProcessedEventStore implements ProcessedEventStore {
        private final Set<UUID> processed = new HashSet<>();

        @Override
        public boolean alreadyProcessed(UUID eventId) {
            return processed.contains(eventId);
        }

        @Override
        public void markProcessed(UUID eventId) {
            processed.add(eventId);
        }
    }
}
