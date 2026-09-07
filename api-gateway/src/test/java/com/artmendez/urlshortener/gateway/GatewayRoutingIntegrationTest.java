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
 * Verifies the Gateway's actual routing, including the dynamic V1/V2 dispatch
 * ({@link DynamicShortCodeRoutingFilter}) that {@link GatewayRoutesConfig}'s own history-long
 * comment always said was still pending -- see that filter's Javadoc for the full context. Fake
 * V1/V2 backends (com.sun.net.httpserver.HttpServer, already in the JDK, avoiding an HTTP-mocking
 * dependency just for this test) each echo back which backend received the request and on what
 * path, so routing is verified by real behavior, not by asserting a target the test itself
 * computed the same way the code does.
 *
 * <p>The fake V2 backend answers a {@code GET} probe (what {@link DynamicShortCodeRoutingFilter}
 * now sends to decide routing) with {@code 404} for one specific path and {@code 200} for
 * everything else, standing in for V2's real {@code GET /{shortCode}} contract. It also answers
 * any OTHER method with {@code 401}, mimicking V2's real {@code SecurityConfig} (which permits
 * unauthenticated access to {@code GET /{shortCode}} specifically, nothing else) -- this is the
 * regression test for the real defect this filter shipped with initially: probing with
 * {@code HEAD} instead of {@code GET} got a {@code 401} from V2's security config, which this
 * filter's "anything but 404 means it exists" rule then misread as "exists in V2". Without this
 * 401-on-non-GET behavior in the fake, that bug passed every test in this class -- see
 * {@link DynamicShortCodeRoutingFilter}'s own Javadoc and AI_USAGE_LOG.md.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingIntegrationTest {

    private static final String NOT_IN_V2_PATH = "/NotInV2Code";

    private static HttpServer fakeV1;
    private static HttpServer fakeV2;

    @BeforeAll
    static void startFakeBackends() throws IOException {
        fakeV1 = startEchoServer("V1", null, false);
        fakeV2 = startEchoServer("V2", NOT_IN_V2_PATH, true);
    }

    @AfterAll
    static void stopFakeBackends() {
        fakeV1.stop(0);
        fakeV2.stop(0);
    }

    private static HttpServer startEchoServer(
            String label, String missingPath, boolean rejectNonGetLikeV2Security) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            if (rejectNonGetLikeV2Security && !"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(401, -1);
                exchange.close();
                return;
            }
            String path = exchange.getRequestURI().getPath();
            if (path.equals(missingPath)) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            String response = label + ":" + path;
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return server;
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("app.v1-legacy-monolith.base-url",
                () -> "http://localhost:" + fakeV1.getAddress().getPort());
        registry.add("app.v2-shortener-service.base-url",
                () -> "http://localhost:" + fakeV2.getAddress().getPort());
    }

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void routesApiV1RequestsToLegacyMonolith() {
        webTestClient.get().uri("/api/v1/urls")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).isEqualTo("V1:/api/v1/urls"));
    }

    @Test
    void routesApiV2RequestsToShortenerService() {
        webTestClient.get().uri("/api/v2/urls")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).isEqualTo("V2:/api/v2/urls"));
    }

    @Test
    void routesShortCodeToLegacyMonolithWhenV2SaysItDoesNotExist() {
        webTestClient.get().uri(NOT_IN_V2_PATH)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).isEqualTo("V1:" + NOT_IN_V2_PATH));
    }

    @Test
    void routesShortCodeToV2WhenV2SaysItExists() {
        webTestClient.get().uri("/InV2Code")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).isEqualTo("V2:/InV2Code"));
    }
}
