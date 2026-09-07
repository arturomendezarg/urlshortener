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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves {@link DynamicShortCodeRoutingFilter} fails safe to V1 when V2 itself cannot be reached
 * to answer the existence check -- its documented fallback (see that class's Javadoc) -- rather
 * than the redirect breaking outright.
 *
 * <p>Replaces this project's former Redis-outage test of the same filter: now that the filter
 * asks V2 directly instead of consulting a separate Redis index, "V2 is unreachable" is the one
 * failure mode that matters here -- there is no longer a second, independent piece of shared
 * infrastructure to fail on its own. Unlike the old test, this one needs no container to stop
 * mid-test: V2's base URL simply points at a port nothing is listening on for the whole test,
 * so every existence check against it fails the same way a real outage would.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DynamicShortCodeRoutingFilterV2OutageTest {

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
        // Nothing listens on this port at all: every existence check against it must fail
        // (connection refused), proving the filter falls back to V1 instead of the redirect
        // breaking outright.
        registry.add("app.v2-shortener-service.base-url", () -> "http://localhost:1");
    }

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void routesToLegacyMonolithWhenV2IsUnreachable() {
        webTestClient.get().uri("/AnyCode")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).isEqualTo("V1:/AnyCode"));
    }
}
