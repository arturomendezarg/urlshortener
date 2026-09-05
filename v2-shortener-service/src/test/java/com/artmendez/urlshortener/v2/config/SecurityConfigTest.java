package com.artmendez.urlshortener.v2.config;

import com.artmendez.urlshortener.v2.ratelimit.RateLimiter;
import com.artmendez.urlshortener.v2.shortlink.bulk.service.BulkJobService;
import com.artmendez.urlshortener.v2.shortlink.service.ShortLinkService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fast, no-credentials complement to {@link KeycloakResourceServerIntegrationTest} (review
 * feedback on PR #25 asked why a password needed to exist in test code/files at all, and
 * suggested mocking as an alternative — this is that alternative, kept alongside the real one
 * rather than replacing it, since a mock cannot catch a real misconfiguration against the
 * actual identity provider the way the Testcontainers-based test does).
 *
 * <p>{@code @MockBean JwtDecoder} means no issuer-uri is ever contacted — Spring Boot's
 * OAuth2 Resource Server autoconfiguration is satisfied by the mock bean instead of trying to
 * reach a real (or dev-mode, unstarted) Keycloak. {@code SecurityMockMvcRequestPostProcessors
 * .jwt()} injects an authenticated {@code Authentication} directly into the security context,
 * bypassing token decoding entirely — no token string, no password, no client, no network call,
 * anywhere in this test.
 */
@WebMvcTest
@Import(SecurityConfig.class)
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    // @WebMvcTest with no controllers=... attribute scans every @RestController in the app,
    // which now includes ShortLinkController (Task #9) and BulkJobController (Task #10) --
    // their constructors need a ShortLinkService/BulkJobService bean respectively, and this
    // slice does not provide either on its own. Both mocked here purely to let the context
    // load; this test still only exercises SecurityConfig's authorization rules, never either
    // service's behavior. Task #11 then added a RateLimiter dependency to BOTH controllers'
    // constructors, and RateLimiter itself needs a StringRedisTemplate bean unavailable in this
    // slice -- mocked below for the same reason as the two services above.
    @MockBean
    private ShortLinkService shortLinkService;

    @MockBean
    private BulkJobService bulkJobService;

    @MockBean
    private RateLimiter rateLimiter;

    // Deliberately NOT "/api/v2/urls": Task #9 mapped POST there, and Spring MVC replies 405
    // (not 404) to a GET on a path mapped for a different HTTP method. This path has no mapping
    // for any method at all, so it stays a valid stand-in for "unmapped, but past security".
    private static final String UNMAPPED_PATH = "/api/v2/urls/diagnostic-not-mapped";

    @Test
    void rejectsAnUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get(UNMAPPED_PATH)).andExpect(status().isUnauthorized());
    }

    @Test
    void letsAnAuthenticatedRequestThroughSecurity() throws Exception {
        // 404, not 401/403: authentication clears security and reaches DispatcherServlet, which
        // has no handler mapped at this path at all. Same reasoning as
        // KeycloakResourceServerIntegrationTest, just with a mocked authentication instead of a
        // real token from a real Keycloak.
        mockMvc.perform(get(UNMAPPED_PATH).with(jwt())).andExpect(status().isNotFound());
    }
}
