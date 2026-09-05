package com.artmendez.urlshortener.v2.ratelimit;

import java.time.Duration;

/**
 * Thrown by {@link RateLimiter#checkLimit(String, int, Duration)} when a caller has exceeded
 * their configured limit for the current window. Mapped to {@code 429 Too Many Requests} by
 * each controller that calls the rate limiter, with {@link #getRetryAfterSeconds()} echoed back
 * as the response's {@code Retry-After} header.
 */
public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(int limit, Duration window, long retryAfterSeconds) {
        super("Rate limit exceeded: " + limit + " requests per " + window.getSeconds() + "s");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
