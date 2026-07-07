package com.blinkitclone.inventoryservice.infrastructure.cache;

import com.blinkitclone.inventoryservice.application.port.out.StockRepository;
import com.blinkitclone.inventoryservice.domain.model.Stock;
import com.blinkitclone.inventoryservice.infrastructure.persistence.adapter.StockRepositoryAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Decorator that wraps StockRepositoryAdapter with a Redis read-through /
 * write-around cache. Declared @Primary so Spring injects this whenever
 * StockRepository is requested — the application layer (ReserveStockService,
 * SeedStockService) has no idea caching exists.
 *
 * <p>Why the Decorator pattern instead of @Cacheable on StockRepositoryAdapter?
 * The domain's Stock class is not Jackson-serializable (private constructor,
 * factory methods), so we can't annotate StockRepositoryAdapter directly and
 * let Spring serialize Stock into Redis. The decorator gives us a seam where
 * we control the serialization by translating to/from StockCacheEntry before
 * any cache interaction, keeping the domain model clean.
 *
 * <p>Cache invalidation strategy:
 * - Reads: check cache first, fall through to DB on miss, populate cache.
 * - Writes (save/saveAll): write to DB first, then evict the cache entry so
 *   the next read fetches the freshest value from DB. Never write the new
 *   value into the cache from a write path — this avoids a race between two
 *   concurrent writers where the "last writer wins in cache" but a slower
 *   DB write arrives after them.
 * - findAllByProductIdIn: not cached (bulk reads are infrequent and harder
 *   to invalidate atomically; the per-key cache handles the hot single-item
 *   read path that the stock pre-check calls).
 */
@Primary
@Component
public class CachingStockRepository implements StockRepository {

    static final String STOCK_CACHE = "stock";

    private static final Logger log = LoggerFactory.getLogger(CachingStockRepository.class);

    private final StockRepositoryAdapter delegate;
    private final CacheManager cacheManager;

    public CachingStockRepository(StockRepositoryAdapter delegate, CacheManager cacheManager) {
        this.delegate = delegate;
        this.cacheManager = cacheManager;
    }

    @Override
    public Optional<Stock> findByProductId(UUID productId) {
        Cache cache = cacheManager.getCache(STOCK_CACHE);
        if (cache != null) {
            StockCacheEntry cached = cache.get(productId, StockCacheEntry.class);
            if (cached != null) {
                log.debug("Cache hit for product {}", productId);
                return Optional.of(cached.toDomain());
            }
        }

        Optional<Stock> result = delegate.findByProductId(productId);
        result.ifPresent(stock -> {
            if (cache != null) {
                cache.put(productId, StockCacheEntry.from(stock));
            }
        });
        return result;
    }

    @Override
    public Stock save(Stock stock) {
        Stock saved = delegate.save(stock);
        evict(saved.productId());
        return saved;
    }

    @Override
    public List<Stock> saveAll(List<Stock> stocks) {
        List<Stock> saved = delegate.saveAll(stocks);
        saved.forEach(s -> evict(s.productId()));
        return saved;
    }

    @Override
    public List<Stock> findAllByProductIdIn(List<UUID> productIds) {
        return delegate.findAllByProductIdIn(productIds);
    }

    private void evict(UUID productId) {
        Cache cache = cacheManager.getCache(STOCK_CACHE);
        if (cache != null) {
            cache.evict(productId);
            log.debug("Evicted cache for product {}", productId);
        }
    }
}
