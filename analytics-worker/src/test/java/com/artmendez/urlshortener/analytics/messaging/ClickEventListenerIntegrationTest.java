package com.artmendez.urlshortener.analytics.messaging;

import com.artmendez.urlshortener.analytics.domain.ClickEventRecord;
import com.artmendez.urlshortener.analytics.repository.ClickEventRepository;
import com.artmendez.urlshortener.analytics.service.DeviceType;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end test (Task #7) with real Postgres and RabbitMQ containers (Testcontainers, no
 * mocks — consistent with the rest of this project's integration suite).
 *
 * <p>Both tests publish a raw JSON message with a {@code __TypeId__} header set to
 * {@code com.artmendez.urlshortener.v1.messaging.ClickEvent} — the exact fully qualified class
 * name V1's own {@code Jackson2JsonMessageConverter} would send, and a class this module does
 * not have on its classpath. This directly exercises the design decision documented in
 * {@link RabbitConfig}: {@code alwaysConvertToInferredType(true)} makes this consumer ignore
 * that header and deserialize into {@link ClickEventMessage} regardless, so the queue can be
 * fed by producers this module knows nothing about. Sending through the default exchange to
 * this exact queue name would also be silently dropped if this worker's {@code RabbitConfig}
 * did not declare the queue itself — a passing test also confirms that declaration happens.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ClickEventListenerIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    // Same rationale as v1-legacy-monolith's RabbitMQ integration tests: the default "guest"
    // user cannot authenticate over a Testcontainers-mapped port (loopback_users restriction),
    // so a dedicated user is created for the test container.
    @Container
    static final RabbitMQContainer RABBITMQ =
            new RabbitMQContainer("rabbitmq:3.13-management-alpine").withUser("appuser", "appuser_local");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);
    }

    @Autowired
    private AmqpTemplate amqpTemplate;

    @Autowired
    private ClickEventRepository repository;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.messaging.click-events-queue}")
    private String queueName;

    private Message buildForeignProducerMessage(Map<String, Object> payload) throws Exception {
        byte[] body = objectMapper.writeValueAsBytes(payload);
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setHeader("__TypeId__", "com.artmendez.urlshortener.v1.messaging.ClickEvent");
        return new Message(body, properties);
    }

    @Test
    void persistsAnonymizedClickEventPublishedWithForeignProducerTypeHeader() throws Exception {
        String shortCode = "int-test-1";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("shortCode", shortCode);
        payload.put("serviceOrigin", "v1");
        payload.put("occurredAt", OffsetDateTime.parse("2026-09-04T12:00:00Z").toString());
        payload.put("clientIp", "203.0.113.77");
        payload.put("userAgent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        payload.put("referrer", "https://example.com");

        amqpTemplate.send("", queueName, buildForeignProducerMessage(payload));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<ClickEventRecord> saved = repository.findAll();
            assertThat(saved).anySatisfy(record -> {
                assertThat(record.getShortCode()).isEqualTo(shortCode);
                assertThat(record.getServiceOrigin()).isEqualTo("v1");
                assertThat(record.getAnonymizedIp()).isEqualTo("203.0.113.0");
                assertThat(record.getDeviceType()).isEqualTo(DeviceType.DESKTOP);
                assertThat(record.getReferrer()).isEqualTo("https://example.com");
            });
        });
    }

    @Test
    void persistsGracefullyWhenIpAndUserAgentAreMissing() throws Exception {
        String shortCode = "int-test-2";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("shortCode", shortCode);
        payload.put("serviceOrigin", "v1");
        payload.put("occurredAt", OffsetDateTime.parse("2026-09-04T12:05:00Z").toString());
        payload.put("clientIp", null);
        payload.put("userAgent", null);
        payload.put("referrer", null);

        amqpTemplate.send("", queueName, buildForeignProducerMessage(payload));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<ClickEventRecord> saved = repository.findAll();
            assertThat(saved).anySatisfy(record -> {
                assertThat(record.getShortCode()).isEqualTo(shortCode);
                assertThat(record.getAnonymizedIp()).isNull();
                assertThat(record.getDeviceType()).isEqualTo(DeviceType.UNKNOWN);
            });
        });
    }
}
