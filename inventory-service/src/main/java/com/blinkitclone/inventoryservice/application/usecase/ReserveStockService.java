package com.blinkitclone.inventoryservice.application.usecase;

import com.blinkitclone.inventoryservice.application.port.in.ReserveStockUseCase;
import com.blinkitclone.inventoryservice.application.port.out.ProcessedEventStore;
import com.blinkitclone.inventoryservice.application.port.out.StockRepository;
import com.blinkitclone.inventoryservice.domain.exception.InsufficientStockException;
import com.blinkitclone.inventoryservice.domain.model.Stock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * All-or-nothing reservation for every line item in one order. We
 * deliberately validate every item's availability *before* mutating any
 * Stock aggregate, so a shortfall on the last item can never leave the
 * first nine items already decremented. Combined with @Transactional, this
 * means: either the whole order's reservation commits, or none of it does —
 * matching the message consumer's expectation (see
 * OrderCreatedEventListener) that a thrown exception means "nothing was
 * persisted, safe to retry or dead-letter."
 */
@Service
public class ReserveStockService implements ReserveStockUseCase {

    private final StockRepository stockRepository;
    private final ProcessedEventStore processedEventStore;

    public ReserveStockService(StockRepository stockRepository, ProcessedEventStore processedEventStore) {
        this.stockRepository = stockRepository;
        this.processedEventStore = processedEventStore;
    }

    @Override
    @Transactional
    public ReservationResult reserveStock(ReserveStockCommand command) {
        // Idempotency check and the stock mutation below happen in the same transaction,
        // so "reserved stock" and "marked this event processed" can never disagree -
        // a redelivered message is a guaranteed no-op, not a race.
        if (processedEventStore.alreadyProcessed(command.eventId())) {
            return ReservationResult.success(command.orderId());
        }

        List<UUID> productIds = command.items().stream().map(ReserveStockCommand.ReservationItem::productId).toList();

        Map<UUID, Stock> stocksByProduct = stockRepository.findAllByProductIdIn(productIds).stream()
                .collect(java.util.stream.Collectors.toMap(Stock::productId, Function.identity()));

        for (var item : command.items()) {
            Stock stock = stocksByProduct.get(item.productId());
            if (stock == null) {
                return ReservationResult.rejected(command.orderId(), "Unknown product: " + item.productId());
            }
            if (item.quantity() > stock.availableQuantity()) {
                return ReservationResult.rejected(command.orderId(),
                        "Insufficient stock for product " + item.productId());
            }
        }

        try {
            for (var item : command.items()) {
                stocksByProduct.get(item.productId()).reserve(item.quantity());
            }
        } catch (InsufficientStockException ex) {
            // Should not happen given the pre-check above under normal operation, but a concurrent
            // reservation between the check and here is possible without row-level locking - which
            // is itself a documented gap to close in Phase 3 (optimistic locking via @Version).
            return ReservationResult.rejected(command.orderId(), ex.getMessage());
        }

        stockRepository.saveAll(List.copyOf(stocksByProduct.values()));
        processedEventStore.markProcessed(command.eventId());
        return ReservationResult.success(command.orderId());
    }
}
