package com.artmendez.urlshortener.bulk.messaging;

import com.artmendez.urlshortener.bulk.repository.ShortLinkRepository;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end test with real Postgres and RabbitMQ containers (Testcontainers, no mocks —
 * consistent with the rest of this project's integration suite; same container setup as
 * {@code analytics-worker}'s {@code ClickEventListenerIntegrationTest}).
 *
 * <p>Fixture rows (the {@code bulk_jobs}/{@code bulk_job_items} state v2-shortener-service would
 * have already committed before publishing) are inserted via a plain {@link JdbcTemplate}, not
 * this module's own {@code BulkJob}/{@code BulkJobItem} entities: those are deliberately
 * write-side-only (no public creation constructor — see their Javadoc), since this module never
 * creates these rows in production, only updates existing ones.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class BulkJobListenerIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    // Same rationale as v1-legacy-monolith's and analytics-worker's RabbitMQ integration tests:
    // the default "guest" user cannot authenticate over a Testcontainers-mapped port.
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
        // Shrink the retry backoff for this test only, so the dead-letter test doesn't have to
        // wait out the real production backoff (see application.yml's app.messaging.retry.*).
        registry.add("app.messaging.retry.max-attempts", () -> "2");
        registry.add("app.messaging.retry.initial-interval-ms", () -> "20");
        registry.add("app.messaging.retry.max-interval-ms", () -> "50");
    }

    @Autowired
    private AmqpTemplate amqpTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ShortLinkRepository shortLinkRepository;

    @Value("${app.messaging.bulk-url-jobs-queue}")
    private String queueName;

    @Value("${app.messaging.bulk-url-jobs-dlq}")
    private String dlqName;

    private Long insertJob(String ownerUserId, int totalItems) {
        jdbcTemplate.update(
                "INSERT INTO bulk_jobs (owner_user_id, status, total_items, processed_items, failed_items, created_at) "
                        + "VALUES (?, 'PENDING', ?, 0, 0, now())",
                ownerUserId, totalItems);
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM bulk_jobs", Long.class);
    }

    private void insertItem(Long jobId, int lineIndex, String longUrl, String customAlias) {
        jdbcTemplate.update(
                "INSERT INTO bulk_job_items (bulk_job_id, line_index, long_url, custom_alias, status) "
                        + "VALUES (?, ?, ?, ?, 'PENDING')",
                jobId, lineIndex, longUrl, customAlias);
    }

    private String jobStatus(Long jobId) {
        return jdbcTemplate.queryForObject("SELECT status FROM bulk_jobs WHERE id = ?", String.class, jobId);
    }

    @Test
    void handle_processesAllItemsAndCompletesTheJob() {
        Long jobId = insertJob("user-int-1", 2);
        insertItem(jobId, 0, "https://example.com/one", null);
        insertItem(jobId, 1, "https://example.com/two", "int-custom-alias");

        amqpTemplate.convertAndSend(queueName, new BulkJobMessage(jobId));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(jobStatus(jobId)).isEqualTo("COMPLETED"));

        Integer processed =
                jdbcTemplate.queryForObject("SELECT processed_items FROM bulk_jobs WHERE id = ?", Integer.class, jobId);
        assertThat(processed).isEqualTo(2);
        assertThat(shortLinkRepository.existsByShortCode("int-custom-alias")).isTrue();
    }

    @Test
    void handle_marksAnInvalidItemFailedAndStillCompletesTheOthers() {
        Long jobId = insertJob("user-int-2", 2);
        insertItem(jobId, 0, "ftp://example.com/bad-scheme", null);
        insertItem(jobId, 1, "https://example.com/good", null);

        amqpTemplate.convertAndSend(queueName, new BulkJobMessage(jobId));

        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(jobStatus(jobId)).isEqualTo("COMPLETED_WITH_ERRORS"));

        String errorMessage = jdbcTemplate.queryForObject(
                "SELECT error_message FROM bulk_job_items WHERE bulk_job_id = ? AND line_index = 0",
                String.class, jobId);
        assertThat(errorMessage).contains("scheme");
    }

    @Test
    void handle_withAJobIdThatDoesNotExistDoesNotDeadLetterTheMessage() throws InterruptedException {
        amqpTemplate.convertAndSend(queueName, new BulkJobMessage(999_999_999L));

        // BulkJobProcessingService logs and returns for a missing job (see its own Javadoc) --
        // it is not an error, so the message must be acknowledged normally, never dead-lettered.
        // A fixed wait (rather than awaitility's untilAsserted) is deliberate here: this test
        // asserts something did NOT happen, so there is no condition to poll toward.
        Thread.sleep(2000);
        assertThat(amqpTemplate.receive(dlqName)).isNull();
    }

    @Test
    void handle_aMalformedMessageEndsUpInTheDeadLetterQueueAfterRetriesAreExhausted() {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        Message poison = new Message("{ not valid json".getBytes(StandardCharsets.UTF_8), properties);

        amqpTemplate.send("", queueName, poison);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            Message dead = amqpTemplate.receive(dlqName);
            assertThat(dead).isNotNull();
        });
    }
}
