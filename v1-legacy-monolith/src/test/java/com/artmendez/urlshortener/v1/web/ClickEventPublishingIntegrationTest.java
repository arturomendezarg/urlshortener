package com.artmendez.urlshortener.v1.web;

import com.artmendez.urlshortener.v1.V1LegacyMonolithApplication;
import com.artmendez.urlshortener.v1.domain.UrlRecord;
import com.artmendez.urlshortener.v1.messaging.ClickEvent;
import com.artmendez.urlshortener.v1.repository.UrlRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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

    // El usuario "guest" por defecto de RabbitMQ solo puede conectarse desde loopback real
    // (restriccion loopback_users). Una conexion via el puerto mapeado de Testcontainers no se ve
    // como loopback desde la perspectiva del broker, asi que "guest" falla el handshake AMQP con
    // un IOException de bajo nivel (no un error de autenticacion limpio). Se crea un usuario
    // dedicado sin esa restriccion, en vez de usar las credenciales por defecto.
    @Container
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management-alpine")
            .withUser("appuser", "appuser_local");

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

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private UrlRecordRepository repository;

    /**
     * Declara la cola explicitamente antes de cada prueba (idempotente: declarar una cola que ya
     * existe con las mismas propiedades no falla). V1 en produccion deliberadamente NO la declara
     * (ver {@code RabbitConfig}) — aqui hace falta para verificar que el mensaje realmente llega
     * a algun lado, cumpliendo temporalmente el rol que el Analytics Worker (Tarea #7, el
     * consumidor real) tendra en produccion.
     *
     * <p>Nota: un bean {@code Queue} dentro de un {@code @TestConfiguration} anidado NO se
     * auto-declara aqui porque {@code @SpringBootTest(classes = ...)} usa una configuracion
     * explicita, lo cual desactiva la auto-deteccion de clases de configuracion anidadas de
     * Spring Boot Test. Declarar imperativamente via {@code RabbitAdmin} evita esa ambiguedad.
     */
    @BeforeEach
    void declareClickEventsQueue() {
        rabbitAdmin.declareQueue(new Queue(QUEUE_NAME, false, false, true));
    }

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
