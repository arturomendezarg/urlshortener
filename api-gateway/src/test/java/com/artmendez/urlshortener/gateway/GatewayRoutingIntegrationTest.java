package com.artmendez.urlshortener.gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the Gateway's actual routing using a fake HTTP server as the V1 backend
 * (com.sun.net.httpserver.HttpServer, already included in the JDK, to avoid adding an HTTP
 * mocking dependency just for this test). The fake responds with the path it received, so
 * we confirm the Gateway forwards the correct path to the correct backend.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingIntegrationTest {

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
    static void overrideV1Url(DynamicPropertyRegistry registry) {
        registry.add("app.v1-legacy-monolith.base-url",
                () -> "http://localhost:" + fakeV1.getAddress().getPort());
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
    void routesRootShortCodeToLegacyMonolith() {
        webTestClient.get().uri("/AbC123")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).isEqualTo("V1:/AbC123"));
    }

    @Test
    void apiV2ReturnsNotImplementedStub() {
        webTestClient.get().uri("/api/v2/urls")
                .exchange()
                .expectStatus().isEqualTo(501);
    }
}
