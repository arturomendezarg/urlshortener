package com.artmendez.urlshortener.v2.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeAll;
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

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end proof (Task #8) that {@link SecurityConfig} is a working OAuth2 Resource Server
 * against a REAL Keycloak instance, not a mocked JWT decoder. {@link SecurityConfigTest} covers
 * the same authorization rules with a mocked JWT and no real IdP, for fast day-to-day feedback —
 * this test exists alongside it because a mock can prove the authorization rules are correct,
 * but only a real Keycloak can catch a misconfigured issuer-uri, a wrong JWK endpoint, or any
 * other real integration problem between this service and the actual identity provider.
 *
 * <p>Review feedback (PR #25) pointed out that the test user's password did not need to be
 * hardcoded a second time here on top of already living in {@code
 * infra/keycloak/realm-export.json}. It doesn't: {@link #loadTestUserCredentials()} reads both
 * the username and password directly out of that same file, so this file is the single source
 * of truth for the credential value, not a second copy that could drift (that value itself is a
 * throwaway local dev/test credential, in the same category as the Postgres/RabbitMQ/Keycloak
 * admin passwords already committed as literal defaults in {@code docker-compose.yml} — see
 * ARCHITECTURE.md section 8.2 "no real credentials, ever" — not a secret worth protecting).
 *
 * <p>Uses the exact same {@code quay.io/keycloak/keycloak:26.0} image that docker-compose uses
 * for local/Codespace dev. Keycloak's dev-mode "request-based" hostname provider derives the
 * token issuer from whatever host/port the token request itself was made through. Both the
 * token request in {@link #obtainAccessToken()} and this test's {@code
 * spring.security.oauth2.resourceserver.jwt.issuer-uri} (set in {@link #configureProperties})
 * are built from the SAME container host/port for exactly that reason — a mismatch here is the
 * most common way this kind of test breaks.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class KeycloakResourceServerIntegrationTest {

    private static final String REALM = "urlshortener";
    private static final String CLIENT_ID = "url-shortener-v2";

    // Not implemented until Task #9 — used here purely as a stand-in path to exercise the
    // security filter chain, not as a test of any business behavior.
    private static final String UNIMPLEMENTED_V2_ENDPOINT = "/api/v2/urls";

    private static String testUsername;
    private static String testPassword;

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

    @BeforeAll
    static void loadTestUserCredentials() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode realm = mapper.readTree(new File(System.getProperty("keycloak.realm-export.path")));
        JsonNode firstUser = realm.path("users").path(0);
        testUsername = firstUser.path("username").asText();
        testPassword = firstUser.path("credentials").path(0).path("value").asText();
    }

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
        form.add("username", testUsername);
        form.add("password", testPassword);

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
    void actuatorHealthIsPubliclyReachableWithNoToken() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + appPort + "/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void rejectsRequestsWithNoTokenBeforeTheyReachAnyHandler() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + appPort + UNIMPLEMENTED_V2_ENDPOINT, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void acceptsARealTokenIssuedByKeycloakAndLetsItThroughSecurity() {
        String accessToken = obtainAccessToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + appPort + UNIMPLEMENTED_V2_ENDPOINT,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);

        // 404, not 401/403: the token cleared security and reached DispatcherServlet, which has
        // no handler for this path yet (Task #9). That is exactly what proves the token was
        // valid -- an invalid or missing token would still be rejected with 401 at this same URL.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
