package com.artmendez.urlshortener.gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
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
 * Verifies the Gateway's actual routing, including the dynamic V1/V2 dispatch
 * ({@link DynamicShortCodeRoutingFilter}) that {@link GatewayRoutesConfig}'s own history-long
 * comment always said was still pending -- see that filter's Javadoc for the full context. Fake
 * V1/V2 backends (com.sun.net.httpserver.HttpServer, already in the JDK, avoiding an HTTP-mocking
 * dependency just for this test) each echo back which backend received the request and on what
 * path, so routing is verified by real behavior, not by asserting a target the test itself
 * computed the same way the code does.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingIntegrationTest {

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static HttpServer fakeV1;
    private static HttpServer fakeV2;

    @BeforeAll
    static void startFakeBackends() throws IOException {
        fakeV1 = startEchoServer("V1");
        fakeV2 = startEchoServer("V2");
    }

    @AfterAll
    static void stopFakeBackends() {
        fakeV1.stop(0);
        fakeV2.stop(0);
    }

    private static HttpServer startEchoServer(String label) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            String response = label + ":" + exchange.getRequestURI().getPath();
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
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ReactiveStringRedisTemplate redisTemplate;

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
    void routesShortCodeToLegacyMonolithWhenAbsentFromTheV2Index() {
        webTestClient.get().uri("/NotInV2")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).isEqualTo("V1:/NotInV2"));
    }

    @Test
    void routesShortCodeToV2WhenPresentInTheV2Index() {
        // Written directly into Redis with the same key shape ShortLinkCache itself uses
        // (KEY_PREFIX "shortlink:v2:") -- this test doesn't run V2's own code, only proves the
        // Gateway reacts correctly to the index V2 maintains.
        redisTemplate.opsForValue().set("shortlink:v2:InV2Code", "1").block();

        webTestClient.get().uri("/InV2Code")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).isEqualTo("V2:/InV2Code"));
    }
}
