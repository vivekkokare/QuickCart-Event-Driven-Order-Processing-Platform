package com.blinkitclone.orderservice.infrastructure.inventory;

import com.blinkitclone.orderservice.application.port.out.StockAvailabilityPort;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * Implements StockAvailabilityPort by calling inventory-service's stock API
 * over HTTP, protected by a Resilience4j circuit breaker.
 *
 * <p>Circuit breaker states:
 * <ul>
 *   <li>CLOSED (normal): calls pass through. After {@code slidingWindowSize}
 *       calls, if the failure rate exceeds {@code failureRateThreshold}%,
 *       the circuit opens.</li>
 *   <li>OPEN: calls fail-fast and hit {@code fallbackIsStockAvailable}, which
 *       returns {@code true} so orders are accepted optimistically. The circuit
 *       stays open for {@code waitDurationInOpenState}.</li>
 *   <li>HALF_OPEN: after the wait, {@code permittedNumberOfCallsInHalfOpenState}
 *       test calls go through. If they succeed, the circuit closes; if they
 *       fail, it reopens.</li>
 * </ul>
 *
 * <p>Why optimistic fallback instead of rejecting orders when the circuit is open?
 * Inventory-service down doesn't mean stock is unavailable — it means we can't
 * check. The async OrderCreated event flow (which is durable) will do the
 * authoritative reservation and reject if stock is truly absent. Rejecting orders
 * when inventory-service is down would grind order intake to a halt for an outage
 * that may not actually affect stock — that's a worse failure mode than
 * occasionally accepting an order that can't be fulfilled.
 */
@Component
public class InventoryServiceClient implements StockAvailabilityPort {

    private static final Logger log = LoggerFactory.getLogger(InventoryServiceClient.class);
    private static final String CIRCUIT_BREAKER_NAME = "inventoryService";

    private final RestClient restClient;

    public InventoryServiceClient(
            @Value("${app.inventory-service.base-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "fallbackIsStockAvailable")
    public boolean isStockAvailable(UUID productId, int requiredQuantity) {
        StockAvailabilityResponse response = restClient.get()
                .uri("/api/v1/stock/{productId}", productId)
                .retrieve()
                .body(StockAvailabilityResponse.class);

        return response != null && response.availableQuantity() >= requiredQuantity;
    }

    @SuppressWarnings("unused")
    private boolean fallbackIsStockAvailable(UUID productId, int requiredQuantity, Exception ex) {
        log.warn("inventory-service circuit breaker active for product {} — allowing order optimistically. Cause: {}",
                productId, ex.getMessage());
        return true;
    }
}
