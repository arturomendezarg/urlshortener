package com.artmendez.urlshortener.v1.web;

import com.artmendez.urlshortener.v1.V1LegacyMonolithApplication;
import com.artmendez.urlshortener.v1.domain.UrlRecord;
import com.artmendez.urlshortener.v1.repository.UrlRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas end-to-end del comportamiento NUEVO agregado por la Tarea #5 (Brownfield):
 * expiracion de URLs via {@code expires_at}. Complementa (sin reemplazar) a
 * {@link UrlBrownfieldCharacterizationTest}, que congela el comportamiento previo.
 */
@Testcontainers
@SpringBootTest(classes = V1LegacyMonolithApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class UrlExpirationIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UrlRecordRepository repository;

    @Test
    void create_withFutureExpiresAt_echoesItBackAndStillRedirects() throws Exception {
        OffsetDateTime future = OffsetDateTime.now().plusDays(1).withNano(0);
        String payload = objectMapper.writeValueAsString(
                new UrlController.CreateUrlRequest("https://example.com/expires-tomorrow", future));

        String response = mockMvc.perform(post("/api/v1/urls")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expiresAt").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String shortCode = objectMapper.readTree(response).get("shortCode").asText();

        mockMvc.perform(get("/" + shortCode))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string("Location", "https://example.com/expires-tomorrow"));
    }

    @Test
    void redirect_forRowWithPastExpiresAt_returns410Gone() throws Exception {
        UrlRecord expiredRow = repository.save(new UrlRecord(
                "expold",
                "https://example.com/already-expired",
                OffsetDateTime.now().minusDays(2),
                OffsetDateTime.now().minusDays(1)));

        mockMvc.perform(get("/" + expiredRow.getShortCode()))
                .andExpect(status().isGone());
    }

    @Test
    void create_withoutExpiresAtField_defaultsToNullAndNeverExpires() throws Exception {
        String payload = objectMapper.writeValueAsString(
                new UrlController.CreateUrlRequest("https://example.com/no-expiry-field"));

        mockMvc.perform(post("/api/v1/urls")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expiresAt").doesNotExist());
    }
}
