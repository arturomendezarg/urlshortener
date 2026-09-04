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
 * Characterization tests of Monolith V1 (Scenario B — Brownfield, see ARCHITECTURE.md
 * section 6): they freeze V1's CURRENT behavior before touching anything, so that zero
 * regressions can be verified once {@code expires_at} is added in a later PR on this
 * same branch.
 *
 * <p>They deliberately do NOT know about the concept of expiration yet (because the code does
 * not know about it either at the time they are written). The success criterion for the
 * Brownfield task is that these tests keep passing with NO changes after the column and the
 * expiration logic are added — if any of them needs to be modified to keep passing, that is a
 * sign of a regression.
 */
@Testcontainers
@SpringBootTest(classes = V1LegacyMonolithApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class UrlBrownfieldCharacterizationTest {

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
    void createUrl_withoutExpirationConcept_returns201WithExactCurrentShape() throws Exception {
        String payload = objectMapper.writeValueAsString(
                new UrlController.CreateUrlRequest("https://example.com/characterization"));

        mockMvc.perform(post("/api/v1/urls")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").exists())
                .andExpect(jsonPath("$.longUrl").value("https://example.com/characterization"))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void redirect_forExistingLegacyRow_returns301WithOriginalLongUrl() throws Exception {
        // Simulates a row that already existed BEFORE this Brownfield task (created directly via
        // the repository, as if it came from before expires_at existed).
        UrlRecord legacyRow = repository.save(
                new UrlRecord("legacy1", "https://example.com/pre-existing-row", OffsetDateTime.now()));

        mockMvc.perform(get("/" + legacyRow.getShortCode()))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string("Location", "https://example.com/pre-existing-row"));
    }

    @Test
    void redirect_forUnknownCode_returns404() throws Exception {
        mockMvc.perform(get("/ZZZZZZ-charz"))
                .andExpect(status().isNotFound());
    }
}
