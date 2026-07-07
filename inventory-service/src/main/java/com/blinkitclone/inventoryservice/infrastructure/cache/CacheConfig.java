package com.blinkitclone.inventoryservice.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/**
 * Configures Redis as the backing store for Spring's @Cacheable abstraction.
 *
 * <p>Why JSON serialization instead of Java's default serialization?
 * Java serialization ties the cached bytes to a specific class structure —
 * a field rename or reorder across deployments produces
 * InvalidClassException on deserialization. JSON is forgiving: unknown
 * fields are ignored and field order is irrelevant, which is critical when
 * cache entries might outlive a deploy. It also makes cache contents
 * inspectable via redis-cli rather than requiring a custom deserializer
 * to understand them.
 *
 * <p>TTL of 5 minutes: stock quantities change on every order, so a
 * long-lived cache would serve stale data and potentially let
 * over-reservations through the optimistic pre-check in order-service.
 * 5 minutes balances read-path relief against staleness risk.
 */
@Configuration
public class CacheConfig {

    @Bean
    public RedisCacheConfiguration redisCacheConfiguration() {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());

        Jackson2JsonRedisSerializer<StockCacheEntry> serializer =
                new Jackson2JsonRedisSerializer<>(objectMapper, StockCacheEntry.class);

        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .disableCachingNullValues()
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(serializer));
    }
}
