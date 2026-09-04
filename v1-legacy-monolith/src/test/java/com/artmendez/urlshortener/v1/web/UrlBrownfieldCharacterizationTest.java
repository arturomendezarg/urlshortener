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
 * Characterization tests del Monolito V1 (Escenario B — Brownfield, ver ARCHITECTURE.md
 * seccion 6): congelan el comportamiento ACTUAL de V1 antes de tocar nada, para poder probar
 * cero regresiones una vez que se agregue {@code expires_at} en un PR posterior sobre esta
 * misma rama.
 *
 * <p>Deliberadamente NO conocen el concepto de expiracion todavia (porque el codigo tampoco lo
 * conoce en el momento en que se escriben). El criterio de exito de la tarea de Brownfield es
 * que estos tests seguirán pasando sin ningún cambio después de agregar la columna y la lógica
 * de expiración — si alguno necesita modificarse para seguir pasando, es señal de una regresión.
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
        // Simula una fila que ya existia ANTES de esta tarea de Brownfield (creada directo por
        // repositorio, como si viniera de antes de que expires_at existiera).
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
