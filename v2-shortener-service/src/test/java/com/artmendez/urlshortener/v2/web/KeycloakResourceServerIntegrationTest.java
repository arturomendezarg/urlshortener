package com.artmendez.urlshortener.v2.web;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end proof (Task #8) that this service is a working OAuth2 Resource Server against a
 * REAL Keycloak instance, not a mocked JWT decoder. Uses the exact same {@code
 * quay.io/keycloak/keycloak:26.0} image and the exact same {@code
 * infra/keycloak/realm-export.json} that docker-compose imports for local/Codespace dev (path
 * injected via the {@code keycloak.realm-export.path} system property — see this module's
 * {@code pom.xml}), so there is a single source of truth for the realm definition, not a second
 * copy that could drift.
 *
 * <p>Keycloak's dev-mode "request-based" hostname provider derives the token issuer from
 * whatever host/port the token request itself was made through. Both the token request in
 * {@link #obtainAccessToken()} and this test's {@code spring.security.oauth2.resourceserver
 * .jwt.issuer-uri} (set in {@link #configureProperties}) are built from the SAME container
 * host/port for exactly that reason — a mismatch here is the most common way this kind of test
 * breaks.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class KeycloakResourceServerIntegrationTest {

    private static final String REALM = "urlshortener";
    private static final String CLIENT_ID = "url-shortener-v2";
    private static final String TEST_USERNAME = "demo";
    private static final String TEST_PASSWORD = "demo_local";

    @Container
    static final GenericContainer<?> KEYCLOAK = new GenericContainer<>("quay.io/keycloak/keycloak:26.0")
            .withCommand("start-dev", "--import-realm")
            .withEnv("KEYCLOAK_ADMIN", "admin")
            .withEnv("KEYCLOAK_ADMIN_PASSWORD", "admin_local")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(System.getProperty("keycloak.realm-export.path")),
                    "/opt/keycloak/data/import/realm-export.json")
            .withExposedPorts(8080)
            .waitingFor(Wait.forLogMessage(".*started in.*\\n", 1))
            .withStartupTimeout(Duration.ofMinutes(3));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> keycloakBaseUrl() + "/realms/" + REALM);
    }

    private static String keycloakBaseUrl() {
        return "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080);
    }

    @LocalServerPort
    private int appPort;

    private final TestRestTemplate restTemplate = new TestRestTemplate();

    private String obtainAccessToken() {
        String tokenUrl = keycloakBaseUrl() + "/realms/" + REALM + "/protocol/openid-connect/token";

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", CLIENT_ID);
        form.add("username", TEST_USERNAME);
        form.add("password", TEST_PASSWORD);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                tokenUrl,
                HttpMethod.POST,
                new HttpEntity<>(form, headers),
                new ParameterizedTypeReference<Map<String, Object>>() { });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) response.getBody().get("access_token");
    }

    @Test
    void whoAmIRejectsRequestsWithNoToken() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + appPort + "/api/v2/_internal/whoami", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void whoAmIAcceptsARealTokenIssuedByKeycloakAndReturnsItsClaims() {
        String accessToken = obtainAccessToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                "http://localhost:" + appPort + "/api/v2/_internal/whoami",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<Map<String, Object>>() { });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("username")).isEqualTo(TEST_USERNAME);
        assertThat((String) response.getBody().get("issuer")).contains("/realms/" + REALM);
    }
}
