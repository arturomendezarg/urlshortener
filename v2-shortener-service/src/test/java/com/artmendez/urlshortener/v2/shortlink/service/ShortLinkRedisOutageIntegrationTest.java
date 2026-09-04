package com.artmendez.urlshortener.v2.shortlink.service;

import com.artmendez.urlshortener.v2.shortlink.domain.ShortLink;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Circuit Breaker "chaos test" ARCHITECTURE.md calls for explicitly (section 6, Scenario A
 * validation note, translated: "local load test verifying the PostgreSQL fallback produces no
 * 5xx errors while Redis is down... a manual chaos test: stop the Redis container midway
 * through"): create a link so it round-trips through Redis at least once, kill the Redis
 * container
 * mid-test, and prove the redirect still resolves correctly straight from PostgreSQL — no
 * exception, no 5xx, just a cache that is temporarily not helping.
 *
 * <p>Kept as its own test class (not a method inside {@link ShortLinkServiceIntegrationTest})
 * specifically so that stopping Redis here cannot affect any other test's shared container —
 * this class owns a Redis instance whose lifecycle it is allowed to end.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ShortLinkRedisOutageIntegrationTest {

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
    private ShortLinkService service;

    @Test
    void redirectStillResolvesFromPostgresWhenRedisGoesDownMidTest() {
        ShortLink created = service.create("https://example.com/resilient", null, null, null, "user-1");

        // Warm the cache with a first successful resolve while Redis is still up.
        String firstTarget = service.resolve(created.getShortCode(), null);
        assertThat(firstTarget).isEqualTo("https://example.com/resilient");

        REDIS.stop();

        // With Redis unreachable, ShortLinkCache's Circuit-Breaker-protected get()/put() calls
        // fail fast and fall back to treating it as a cache miss / a skipped write (see that
        // class's Javadoc) -- the redirect itself must still succeed straight from PostgreSQL,
        // with no exception reaching this caller and, by extension, no 5xx at the controller.
        String targetWithRedisDown = service.resolve(created.getShortCode(), null);

        assertThat(targetWithRedisDown).isEqualTo("https://example.com/resilient");
    }
}
