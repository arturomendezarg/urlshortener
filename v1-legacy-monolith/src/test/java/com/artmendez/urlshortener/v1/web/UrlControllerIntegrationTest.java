package com.artmendez.urlshortener.v1.web;

import com.artmendez.urlshortener.v1.V1LegacyMonolithApplication;
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

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end test of Monolith V1 using Testcontainers (real Postgres, not H2) to validate
 * the full flow: create URL -> Liquibase applies the schema -> persist -> redirect.
 */
@Testcontainers
@SpringBootTest(classes = V1LegacyMonolithApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class UrlControllerIntegrationTest {

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

    @Test
    void createAndRedirect_endToEnd() throws Exception {
        String payload = objectMapper.writeValueAsString(
                new UrlController.CreateUrlRequest("https://example.com/prueba"));

        String response = mockMvc.perform(post("/api/v1/urls")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value(matchesPattern("[A-Za-z0-9]{6}")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String shortCode = objectMapper.readTree(response).get("shortCode").asText();

        mockMvc.perform(get("/" + shortCode))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string("Location", "https://example.com/prueba"));
    }

    @Test
    void redirect_returns404ForUnknownCode() throws Exception {
        mockMvc.perform(get("/ZZZZZZ"))
                .andExpect(status().isNotFound());
    }
}
