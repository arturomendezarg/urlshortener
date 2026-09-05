package com.artmendez.urlshortener.v2.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Mirrors {@link
 * com.artmendez.urlshortener.v2.shortlink.service.ShortLinkRedisOutageIntegrationTest}'s chaos
 * test for the "redis" Circuit Breaker instance, but for {@link RateLimiter#checkLimit} instead
 * of the redirect cache: stop Redis mid-test and prove {@code checkLimit} fails open (see {@link
 * RateLimiter}'s Javadoc on why unavailable Redis must never block link creation) rather than
 * throwing.
 *
 * <p>Kept as its own test class, not a method inside {@link RateLimiterTest}, for the exact same
 * reason {@code ShortLinkRedisOutageIntegrationTest} is separate from the rest of the redirect
 * cache's tests: stopping Redis here must not affect any other test's shared container.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class RateLimiterRedisOutageTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private RateLimiter rateLimiter;

    @Test
    void allowsTheCallThroughWhenRedisIsUnreachable() {
        REDIS.stop();

        // With Redis unreachable, checkLimit's Circuit-Breaker-protected Redis calls fail fast
        // and fall back to allowOnRedisOutage (see RateLimiter's Javadoc) -- the call must
        // complete with no exception reaching this caller, same as an unlimited caller would.
        assertThatCode(() -> rateLimiter.checkLimit("ratelimit-outage-test", 1, Duration.ofSeconds(60)))
                .doesNotThrowAnyException();
    }
}
