package com.artmendez.urlshortener.gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.reactive.server.WebTestClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica el enrutamiento real del Gateway usando un servidor HTTP fake como backend V1
 * (com.sun.net.httpserver.HttpServer, ya incluido en el JDK, para no agregar una dependencia
 * de mocking HTTP solo para esta prueba). El fake responde con la ruta que recibio, asi
 * confirmamos que el Gateway reenvia la ruta correcta al backend correcto.
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
