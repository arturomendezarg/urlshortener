package com.artmendez.urlshortener.v2.shortlink.web;

import com.artmendez.urlshortener.v2.config.SecurityConfig;
import com.artmendez.urlshortener.v2.shortlink.domain.ShortLink;
import com.artmendez.urlshortener.v2.shortlink.service.DuplicateAliasException;
import com.artmendez.urlshortener.v2.validation.InvalidLongUrlException;
import com.artmendez.urlshortener.v2.shortlink.service.ReservedSlugException;
import com.artmendez.urlshortener.v2.shortlink.service.ShortLinkExpiredException;
import com.artmendez.urlshortener.v2.shortlink.service.ShortLinkNotFoundException;
import com.artmendez.urlshortener.v2.shortlink.service.ShortLinkService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link ShortLinkController}: authorization (via the real
 * {@link SecurityConfig}, not disabled) and the HTTP status mapping for every outcome
 * {@link ShortLinkService} can produce. {@link ShortLinkService} itself is mocked here — its own
 * behavior is covered by {@code ShortLinkServiceTest} and the Testcontainers integration test.
 */
@WebMvcTest(controllers = ShortLinkController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "app.shortlink.base-url=http://localhost:8084")
class ShortLinkControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private ShortLinkService shortLinkService;

    @BeforeEach
    void setUp() {
        ShortLink shortLink = new ShortLink(
                "abc1234", "https://example.com", "user-123", null, OffsetDateTime.now(), null);
        when(shortLinkService.create(anyString(), any(), any(), any(), anyString())).thenReturn(shortLink);
    }

    @Test
    void create_withoutAuthenticationIsRejected() throws Exception {
        mockMvc.perform(post("/api/v2/urls")
                        .contentType("application/json")
                        .content("{\"longUrl\":\"https://example.com\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_withAValidAuthenticatedRequestReturns201() throws Exception {
        mockMvc.perform(post("/api/v2/urls")
                        .with(jwt())
                        .contentType("application/json")
                        .content("{\"longUrl\":\"https://example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value("abc1234"))
                .andExpect(jsonPath("$.shortUrl").value("http://localhost:8084/abc1234"))
                .andExpect(jsonPath("$.ownerId").value("user-123"));
    }

    @Test
    void create_withABlankLongUrlReturns400() throws Exception {
        mockMvc.perform(post("/api/v2/urls")
                        .with(jwt())
                        .contentType("application/json")
                        .content("{\"longUrl\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_whenTheServiceRejectsAReservedSlugReturns400WithAnErrorBody() throws Exception {
        when(shortLinkService.create(anyString(), eq("admin"), any(), any(), anyString()))
                .thenThrow(new ReservedSlugException("admin"));

        mockMvc.perform(post("/api/v2/urls")
                        .with(jwt())
                        .contentType("application/json")
                        .content("{\"longUrl\":\"https://example.com\",\"customAlias\":\"admin\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void create_whenTheServiceRejectsAnInvalidLongUrlReturns400() throws Exception {
        when(shortLinkService.create(eq("ftp://example.com"), any(), any(), any(), anyString()))
                .thenThrow(new InvalidLongUrlException("longUrl scheme must be http or https"));

        mockMvc.perform(post("/api/v2/urls")
                        .with(jwt())
                        .contentType("application/json")
                        .content("{\"longUrl\":\"ftp://example.com\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_whenTheAliasIsAlreadyTakenReturns409() throws Exception {
        when(shortLinkService.create(anyString(), eq("taken"), any(), any(), anyString()))
                .thenThrow(new DuplicateAliasException("taken"));

        mockMvc.perform(post("/api/v2/urls")
                        .with(jwt())
                        .contentType("application/json")
                        .content("{\"longUrl\":\"https://example.com\",\"customAlias\":\"taken\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void redirect_isReachableWithNoAuthenticationAtAll() throws Exception {
        when(shortLinkService.resolve(eq("abc1234"), any())).thenReturn("https://example.com/target");

        mockMvc.perform(get("/abc1234"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/target"));
    }

    @Test
    void redirect_whenTheCodeDoesNotExistReturns404() throws Exception {
        when(shortLinkService.resolve(eq("missing"), any())).thenThrow(new ShortLinkNotFoundException("missing"));

        mockMvc.perform(get("/missing")).andExpect(status().isNotFound());
    }

    @Test
    void redirect_whenTheLinkIsExpiredReturns410() throws Exception {
        when(shortLinkService.resolve(eq("expired1"), any())).thenThrow(new ShortLinkExpiredException("expired1"));

        mockMvc.perform(get("/expired1")).andExpect(status().isGone());
    }
}
