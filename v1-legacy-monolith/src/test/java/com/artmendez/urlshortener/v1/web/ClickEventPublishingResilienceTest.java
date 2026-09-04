package com.artmendez.urlshortener.v1.web;

import com.artmendez.urlshortener.v1.V1LegacyMonolithApplication;
import com.artmendez.urlshortener.v1.domain.UrlRecord;
import com.artmendez.urlshortener.v1.repository.UrlRecordRepository;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the core acceptance criterion of Task #6: if RabbitMQ is down / unreachable, the
 * redirect (the critical path) must keep working exactly the same. Deliberately does NOT spin
 * up a RabbitMQ container — it targets a local port with nothing listening, to force the
 * connection failure that {@link
 * com.artmendez.urlshortener.v1.messaging.ClickEventPublisher} must absorb.
 */
@Testcontainers
@SpringBootTest(classes = V1LegacyMonolithApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ClickEventPublishingResilienceTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.rabbitmq.host", () -> "localhost");
        registry.add("spring.rabbitmq.port", () -> 59999);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UrlRecordRepository repository;

    @Test
    void redirect_stillReturns301_whenRabbitMqIsUnreachable() throws Exception {
        UrlRecord row = repository.save(new UrlRecord(
                "rmqdwn", "https://example.com/rabbitmq-is-down", OffsetDateTime.now()));

        mockMvc.perform(get("/" + row.getShortCode()))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string("Location", "https://example.com/rabbitmq-is-down"));
    }
}
