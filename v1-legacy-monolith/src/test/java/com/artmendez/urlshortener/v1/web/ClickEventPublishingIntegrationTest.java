package com.artmendez.urlshortener.v1.web;

import com.artmendez.urlshortener.v1.V1LegacyMonolithApplication;
import com.artmendez.urlshortener.v1.domain.UrlRecord;
import com.artmendez.urlshortener.v1.messaging.ClickEvent;
import com.artmendez.urlshortener.v1.repository.UrlRecordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba end-to-end (Tarea #6) de que V1 publica un evento de clic real a RabbitMQ en cada
 * redireccion exitosa. Usa contenedores reales de Postgres y RabbitMQ (Testcontainers), no mocks
 * — coherente con el resto de la suite de integracion del proyecto.
 */
@Testcontainers
@SpringBootTest(classes = V1LegacyMonolithApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ClickEventPublishingIntegrationTest {

    private static final String QUEUE_NAME = "click-events";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", rabbitmq::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitmq::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitmq::getAdminPassword);
    }

    /**
     * Declara la cola SOLO para esta prueba. V1 en produccion deliberadamente no la declara (ver
     * {@code RabbitConfig}) — aqui hace falta para poder verificar que el mensaje realmente
     * llega a algun lado, cumpliendo temporalmente el rol que el Analytics Worker (Tarea #7,
     * el consumidor real) tendra en produccion.
     */
    @TestConfiguration
    static class TestQueueConfig {
        @Bean
        Queue clickEventsQueue() {
            return new Queue(QUEUE_NAME, false, false, true);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private UrlRecordRepository repository;

    @Test
    void redirect_publishesClickEvent_withExpectedFields() throws Exception {
        UrlRecord row = repository.save(new UrlRecord(
                "clkevt", "https://example.com/click-event-target", OffsetDateTime.now()));

        mockMvc.perform(get("/" + row.getShortCode())
                        .header("User-Agent", "IntegrationTestAgent/1.0")
                        .header("Referer", "https://referrer.example.com"))
                .andExpect(status().isMovedPermanently());

        Object received = rabbitTemplate.receiveAndConvert(QUEUE_NAME, 5000);

        assertThat(received).isInstanceOf(ClickEvent.class);
        ClickEvent event = (ClickEvent) received;
        assertThat(event.shortCode()).isEqualTo("clkevt");
        assertThat(event.serviceOrigin()).isEqualTo("v1");
        assertThat(event.userAgent()).isEqualTo("IntegrationTestAgent/1.0");
        assertThat(event.referrer()).isEqualTo("https://referrer.example.com");
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    void redirect_doesNotPublishClickEvent_whenShortCodeIsExpired() throws Exception {
        UrlRecord expired = repository.save(new UrlRecord(
                "expevt",
                "https://example.com/expired-no-event",
                OffsetDateTime.now().minusDays(2),
                OffsetDateTime.now().minusDays(1)));

        mockMvc.perform(get("/" + expired.getShortCode()))
                .andExpect(status().isGone());

        Object received = rabbitTemplate.receiveAndConvert(QUEUE_NAME, 1000);

        assertThat(received).isNull();
    }
}
