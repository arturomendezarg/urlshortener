package com.artmendez.urlshortener.v2.ratelimit;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Redis-backed fixed-window rate limiter (ARCHITECTURE.md, section 5/8, Task #11), guarding
 * {@code POST /api/v2/urls} and {@code POST /api/v2/urls/bulk} against a single authenticated
 * caller creating short links faster than a configured threshold.
 *
 * <p><b>Algorithm:</b> a fixed window, not a sliding window or token bucket (both explicitly
 * allowed by ARCHITECTURE.md) — {@code INCR} on a per-caller-per-endpoint key, with
 * {@code EXPIRE} set only the first time that key is created in the current window. This is a
 * deliberate simplicity trade-off for this exercise's timebox: a fixed window can allow up to
 * {@code 2x limit} requests across a window boundary (a burst just before the window ends,
 * followed immediately by another burst once it resets), which neither a sliding window nor a
 * token bucket would permit. Accepted here because the goal is coarse abuse mitigation, not
 * precise traffic shaping.
 *
 * <p><b>Correctness note:</b> {@code INCR} and {@code EXPIRE} are two separate Redis commands,
 * not one atomic operation. If this process crashed between them, a key could in theory be left
 * without a TTL and lock out that caller/endpoint permanently until manually cleared. This is a
 * declared, accepted risk (an operator would notice and can {@code DEL} the stray key) rather
 * than reaching for a Lua script to make the pair atomic, again a deliberate simplicity choice
 * given this exercise's scope.
 *
 * <p><b>Fail-open on a Redis outage:</b> wrapped in the SAME Resilience4j Circuit Breaker
 * instance ("redis") already used by {@link
 * com.artmendez.urlshortener.v2.shortlink.cache.ShortLinkCache} for the same underlying Redis
 * connection — if Redis is slow or unreachable, the breaker trips and {@link
 * #allowOnRedisOutage} lets the request through rather than blocking short-link creation
 * entirely. Rate limiting is a defense-in-depth abuse mitigation, not a security boundary (that
 * role belongs to {@link com.artmendez.urlshortener.v2.validation.LongUrlValidator}, which fails
 * CLOSED by design) — availability of the core feature must not depend on this limiter's own
 * infrastructure being up.
 *
 * <p><b>{@link RateLimitExceededException} must never be mistaken for a Redis failure</b>, and
 * that takes TWO independent mechanisms, because Resilience4j decides "what the breaker records"
 * and "what the fallback catches" in two different places:
 *
 * <ul>
 *   <li><b>What the breaker records</b> — {@code
 *       resilience4j.circuitbreaker.instances.redis.ignore-exceptions} in {@code application.yml}
 *       lists this exception, so a caller going over their limit is never counted as a Redis
 *       failure. Without it, one caller hammering past the limit would trip the "redis" breaker
 *       open, and since that instance is SHARED with {@link
 *       com.artmendez.urlshortener.v2.shortlink.cache.ShortLinkCache}, it would take the redirect
 *       cache down with it as collateral damage.</li>
 *   <li><b>What the fallback catches</b> — {@code ignore-exceptions} does NOT stop the fallback
 *       from running. The {@code fallbackMethod} decorator sits OUTSIDE the breaker-decorated
 *       call and catches every {@link Throwable} coming out of it, ignored by the breaker or
 *       not. That is why {@link #allowOnRedisOutage} rethrows this exception explicitly instead
 *       of logging it: otherwise the fallback would swallow it and let a rate-limited caller
 *       straight through, exactly backwards.</li>
 * </ul>
 *
 * <p>Assuming the first mechanism also covered the second is precisely what made this class's
 * first CI run fail (three {@code RateLimiterTest} cases saw no exception at all) — recorded in
 * {@code AI_USAGE_LOG.md} rather than quietly fixed, since the distinction is easy to get wrong
 * and invisible until a test asserts on it.
 */
@Component
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private final StringRedisTemplate redisTemplate;

    public RateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * @param key    a caller-and-endpoint-specific Redis key (e.g. {@code
     *               "ratelimit:create-url:<jwt-subject>"}); callers own the key shape so
     *               different endpoints never collide with each other
     * @param limit  maximum number of calls allowed per {@code window}
     * @param window how long each fixed window lasts
     * @throws RateLimitExceededException if this call would exceed {@code limit} within the
     *                                     current window
     */
    @CircuitBreaker(name = "redis", fallbackMethod = "allowOnRedisOutage")
    public void checkLimit(String key, int limit, Duration window) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, window);
        }
        if (count != null && count > limit) {
            Long ttlSeconds = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            long retryAfterSeconds = (ttlSeconds == null || ttlSeconds < 0) ? window.getSeconds() : ttlSeconds;
            throw new RateLimitExceededException(limit, window, retryAfterSeconds);
        }
    }

    /**
     * Invoked by Resilience4j (by name, via the {@code fallbackMethod} above) for EVERY throwable
     * {@link #checkLimit} lets out — whether the "redis" breaker is open, the call to Redis
     * failed, or the limit was simply exceeded. For a genuine Redis problem this deliberately
     * does nothing but log, so link creation keeps working uncapped instead of failing outright;
     * {@link RateLimitExceededException} is rethrown untouched, because it is this limiter's
     * normal, expected answer and not an infrastructure failure at all (see the class Javadoc).
     *
     * <p>Deliberately typed as {@link Throwable} rather than enumerating Redis's own exception
     * types ({@code RedisConnectionFailureException}, {@code QueryTimeoutException},
     * {@code CallNotPermittedException}, ...) in narrower overloads: fail-open is only worth
     * anything if it covers every way the client library can fail, including the ones not
     * thought of here, and the single business exception that must NOT fail open is the one case
     * this method can name exactly.
     */
    @SuppressWarnings("unused")
    private void allowOnRedisOutage(String key, int limit, Duration window, Throwable t) {
        if (t instanceof RateLimitExceededException rateLimitExceeded) {
            throw rateLimitExceeded;
        }
        log.warn("Rate limiter could not reach Redis for key '{}', allowing the request through "
                + "(fail-open): {}", key, t.toString());
    }
}
