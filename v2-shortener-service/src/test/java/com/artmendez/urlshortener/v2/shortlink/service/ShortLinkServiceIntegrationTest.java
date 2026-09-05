package com.artmendez.urlshortener.v2.shortlink.service;

import com.artmendez.urlshortener.v2.shortlink.domain.RedirectDeviceType;
import com.artmendez.urlshortener.v2.shortlink.domain.RedirectRule;
import com.artmendez.urlshortener.v2.shortlink.domain.ShortLink;
import com.artmendez.urlshortener.v2.shortlink.repository.ShortLinkRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end test (Task #9) with real PostgreSQL and Redis containers (Testcontainers, no mocks
 * — consistent with the rest of this project's integration suite), covering
 * {@link ShortLinkService#create} and {@link ShortLinkService#resolve} together: creation,
 * cache-aside population on a cold read, expiration handling, and conditional redirect by
 * device type (ARCHITECTURE.md, section 6, Scenario A and Scenario C).
 *
 * <p>The Redis-outage / Circuit Breaker fallback scenario (ARCHITECTURE.md's own validation
 * note for Scenario A: "chaos test... apagar el contenedor de Redis a mitad de la prueba") is
 * deliberately a SEPARATE test class ({@link ShortLinkRedisOutageIntegrationTest}): stopping the
 * shared Redis container here would break every test that runs after it in this class, since
 * JUnit does not guarantee method execution order.
 */
@Testcontainers
// WebEnvironment.NONE (used by analytics-worker's own Testcontainers tests) does not work
// here: it sets spring.main.web-application-type=none, which skips the servlet-specific
// auto-configuration that SecurityConfig's SecurityFilterChain bean (built from HttpSecurity)
// needs, so the context fails to start at all. MOCK is the lightest environment that still
// boots a real (mock) servlet web application context; a real HTTP call is never made here
// since this test talks to ShortLinkService directly.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class ShortLinkServiceIntegrationTest {

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

    @Autowired
    private ShortLinkRepository repository;

    @Test
    void createThenResolveRoundTripsThroughPostgresAndPopulatesTheCache() {
        ShortLink created = service.create("https://example.com/hello", null, null, null, "user-1");

        String target = service.resolve(created.getShortCode(), "Mozilla/5.0 (Windows NT 10.0)");

        assertThat(target).isEqualTo("https://example.com/hello");
        assertThat(repository.existsByShortCode(created.getShortCode())).isTrue();
    }

    @Test
    void resolvingAnUnknownCodeThrowsNotFound() {
        assertThatThrownBy(() -> service.resolve("does-not-exist", null))
                .isInstanceOf(ShortLinkNotFoundException.class);
    }

    @Test
    void resolvingAnExpiredLinkThrowsExpiredInsteadOfRedirecting() {
        ShortLink created = service.create(
                "https://example.com/soon-gone", null, OffsetDateTime.now().minusMinutes(1), null, "user-1");

        assertThatThrownBy(() -> service.resolve(created.getShortCode(), null))
                .isInstanceOf(ShortLinkExpiredException.class);
    }

    @Test
    void resolvingAppliesTheRedirectRuleMatchingTheRequestingDevice() {
        List<RedirectRule> rules = List.of(
                new RedirectRule(RedirectDeviceType.MOBILE, "https://example.com/mobile"),
                new RedirectRule(RedirectDeviceType.DESKTOP, "https://example.com/desktop"));
        ShortLink created = service.create("https://example.com/default", null, null, rules, "user-1");

        String mobileTarget = service.resolve(
                created.getShortCode(), "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)");
        String desktopTarget = service.resolve(
                created.getShortCode(), "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0");

        assertThat(mobileTarget).isEqualTo("https://example.com/mobile");
        assertThat(desktopTarget).isEqualTo("https://example.com/desktop");
    }

    @Test
    void rejectsACustomAliasThatCollidesWithAnExistingOne() {
        service.create("https://example.com/first", "shared-alias", null, null, "user-1");

        assertThatThrownBy(() ->
                        service.create("https://example.com/second", "shared-alias", null, null, "user-2"))
                .isInstanceOf(DuplicateAliasException.class);
    }
}
