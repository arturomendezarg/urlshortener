package com.artmendez.urlshortener.gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors {@code ShortLinkRedisOutageIntegrationTest} and {@code RateLimiterRedisOutageTest}'s
 * chaos tests (v2-shortener-service): stop Redis mid-test and prove
 * {@link DynamicShortCodeRoutingFilter} fails safe to V1 -- its documented fallback (see that
 * class's Javadoc) -- rather than the redirect breaking outright.
 *
 * <p>Kept as its own test class, not a method inside {@link GatewayRoutingIntegrationTest}, for
 * the same reason those two v2-shortener-service tests are split from their siblings: stopping
 * Redis here must not affect any other test's shared container.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DynamicShortCodeRoutingFilterRedisOutageTest {

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static HttpServer fakeV1;

    @BeforeAll
    static void startFakeV1() throws IOException {
        fakeV1 = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        fakeV1.createContext("/", exchange -> {
            String response = "V1:" + exchange.getRequestURI().getPath();
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        fakeV1.start();
    }

    @AfterAll
    static void stopFakeV1() {
        fakeV1.stop(0);
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("app.v1-legacy-monolith.base-url",
                () -> "http://localhost:" + fakeV1.getAddress().getPort());
        // Deliberately no real V2 backend behind this URL: the whole point of this test is that
        // an outage must never even attempt to reach V2.
        registry.add("app.v2-shortener-service.base-url", () -> "http://localhost:1");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void routesToLegacyMonolithWhenRedisIsUnreachable() {
        REDIS.stop();

        webTestClient.get().uri("/AnyCode")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).isEqualTo("V1:/AnyCode"));
    }
}
