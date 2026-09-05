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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link RateLimiter} against a real Redis, following the same
 * Testcontainers shape as {@link
 * com.artmendez.urlshortener.v2.shortlink.service.ShortLinkRedisOutageIntegrationTest}: a
 * mocked-web {@code @SpringBootTest} is the lightest environment that still lets
 * {@code SecurityConfig}'s {@code SecurityFilterChain} bean build (see that class's Javadoc for
 * why {@code WebEnvironment.NONE} does not work here), and a real {@link
 * org.springframework.data.redis.core.StringRedisTemplate} is what actually exercises the
 * fixed-window {@code INCR}/{@code EXPIRE} logic in {@link RateLimiter#checkLimit}. Redis outage
 * behavior (fail-open via the circuit breaker) is deliberately its own test class, {@link
 * RateLimiterRedisOutageTest}, so that stopping Redis there cannot affect the shared container
 * these tests use.
 *
 * <p>Each test uses a fresh, random key ({@link #uniqueKey()}) rather than sharing one across
 * tests, so that fixed-window counters left over from one test can never leak into another.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class RateLimiterTest {

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

    private static String uniqueKey() {
        return "ratelimit-test:" + UUID.randomUUID();
    }

    @Test
    void allowsCallsUpToTheLimitWithinTheWindow() {
        String key = uniqueKey();

        // A limit of 3 must let exactly 3 calls through with no exception.
        for (int i = 0; i < 3; i++) {
            rateLimiter.checkLimit(key, 3, Duration.ofSeconds(60));
        }
    }

    @Test
    void rejectsTheCallThatWouldExceedTheLimit() {
        String key = uniqueKey();

        rateLimiter.checkLimit(key, 2, Duration.ofSeconds(60));
        rateLimiter.checkLimit(key, 2, Duration.ofSeconds(60));

        // The 3rd call against a limit of 2 is the one that must be rejected.
        //
        // This assertion is also the regression guard for a real bug this class caught on its
        // very first CI run: because @CircuitBreaker's fallbackMethod catches every Throwable
        // the guarded method lets out, the fallback was swallowing RateLimitExceededException
        // and letting rate-limited callers straight through -- and it does so regardless of
        // resilience4j's ignore-exceptions, which only governs what the breaker RECORDS. Going
        // through the real Spring proxy here (rather than calling a plain RateLimiter instance)
        // is what makes this test able to see that at all.
        assertThatThrownBy(() -> rateLimiter.checkLimit(key, 2, Duration.ofSeconds(60)))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("Rate limit exceeded");
    }

    @Test
    void retryAfterSecondsIsBoundedByTheConfiguredWindow() {
        String key = uniqueKey();
        Duration window = Duration.ofSeconds(60);

        rateLimiter.checkLimit(key, 1, window);

        assertThatThrownBy(() -> rateLimiter.checkLimit(key, 1, window))
                .isInstanceOf(RateLimitExceededException.class)
                .satisfies(thrown -> {
                    RateLimitExceededException exceeded = (RateLimitExceededException) thrown;
                    // The key's TTL was just set to the full window, so retryAfterSeconds must
                    // be positive and can never exceed the window itself.
                    assertThat(exceeded.getRetryAfterSeconds()).isPositive();
                    assertThat(exceeded.getRetryAfterSeconds()).isLessThanOrEqualTo(window.getSeconds());
                });
    }

    @Test
    void differentKeysHaveIndependentBudgets() {
        String keyA = uniqueKey();
        String keyB = uniqueKey();

        // Exhaust keyA's budget of 1.
        rateLimiter.checkLimit(keyA, 1, Duration.ofSeconds(60));
        assertThatThrownBy(() -> rateLimiter.checkLimit(keyA, 1, Duration.ofSeconds(60)))
                .isInstanceOf(RateLimitExceededException.class);

        // keyB is a completely independent counter and must still have its own full budget.
        rateLimiter.checkLimit(keyB, 1, Duration.ofSeconds(60));
    }
}
