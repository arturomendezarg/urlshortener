package com.artmendez.urlshortener.v2.shortlink.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Cache-aside adapter around Redis for short-link lookups (ARCHITECTURE.md, section 6, Scenario
 * A). Every Redis call here is wrapped in a Resilience4j Circuit Breaker (instance "redis",
 * tuned in application.yml): when Redis is slow or unreachable, calls fail fast and the
 * annotated fallback method is invoked instead of the exception propagating.
 *
 * <p>This is the fix for the exact gap ARCHITECTURE.md documents as the AI's first draft
 * missing: an initial Redis-backed redirect service handled a cache MISS correctly but not
 * Redis being unreachable (connection refused, timeout) — a real outage would have thrown
 * straight through to the controller as a 5xx. {@link
 * com.artmendez.urlshortener.v2.shortlink.service.ShortLinkService} always has PostgreSQL as the
 * source of truth behind this cache, so treating a Redis failure as a plain cache miss here is
 * safe: the caller falls through to the database, and the redirect still succeeds — just without
 * the speed-up the cache exists to provide, for as long as the breaker stays open.
 */
@Component
public class ShortLinkCache {

    private static final Logger log = LoggerFactory.getLogger(ShortLinkCache.class);
    private static final String KEY_PREFIX = "shortlink:v2:";

    // Typed against the RedisOperations interface rather than the concrete StringRedisTemplate
    // class: good practice on its own (code against the interface a Spring Data template
    // implements), inspired by the RabbitTemplate -> AmqpTemplate fix elsewhere in this project
    // (AI_USAGE_LOG.md, PR #20). It does NOT, on its own, satisfy SpotBugs' EI_EXPOSE_REP2 here
    // the way it did for RabbitTemplate: this field, and objectMapper below, are still live,
    // stateful, unavoidably mutable framework clients with no immutable variant to switch to --
    // see spotbugs-exclude.xml for the actual (narrow, documented) fix.
    private final RedisOperations<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public ShortLinkCache(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${app.shortlink.cache-ttl-seconds}") long ttlSeconds) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    @CircuitBreaker(name = "redis", fallbackMethod = "getFallback")
    public Optional<CachedShortLink> get(String shortCode) {
        String json = redisTemplate.opsForValue().get(KEY_PREFIX + shortCode);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, CachedShortLink.class));
        } catch (JsonProcessingException e) {
            // A corrupt cache entry is treated exactly like a miss, never as an error: the
            // PostgreSQL-backed caller re-populates it right after.
            log.warn("Corrupt cache entry for short code '{}', treating as a cache miss", shortCode, e);
            return Optional.empty();
        }
    }

    @CircuitBreaker(name = "redis", fallbackMethod = "putFallback")
    public void put(String shortCode, CachedShortLink value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(KEY_PREFIX + shortCode, json, ttl);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize short code '{}' for caching, skipping cache write", shortCode, e);
        }
    }

    @SuppressWarnings("unused") // invoked reflectively by Resilience4j as the fallback for get(...)
    private Optional<CachedShortLink> getFallback(String shortCode, Throwable t) {
        log.warn(
                "Redis unavailable while reading short code '{}', falling back to PostgreSQL: {}",
                shortCode, t.toString());
        return Optional.empty();
    }

    @SuppressWarnings("unused") // invoked reflectively by Resilience4j as the fallback for put(...)
    private void putFallback(String shortCode, CachedShortLink value, Throwable t) {
        log.warn(
                "Redis unavailable while caching short code '{}', skipping cache write: {}",
                shortCode, t.toString());
    }
}
