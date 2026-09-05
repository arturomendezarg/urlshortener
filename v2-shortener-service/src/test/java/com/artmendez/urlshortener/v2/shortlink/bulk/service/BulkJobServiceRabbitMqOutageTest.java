package com.artmendez.urlshortener.v2.shortlink.bulk.service;

import com.artmendez.urlshortener.v2.shortlink.bulk.repository.BulkJobItemRepository;
import com.artmendez.urlshortener.v2.shortlink.bulk.repository.BulkJobRepository;
import com.artmendez.urlshortener.v2.shortlink.bulk.web.BulkUrlItemRequest;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Chaos test proving the one guarantee {@link BulkJobServiceTest}'s mock-based
 * {@code createJob_propagatesAPublishFailureRatherThanSwallowingIt} could never actually verify:
 * that a publish failure rolls back the REAL database transaction, leaving no orphaned {@code
 * PENDING} {@code bulk_jobs}/{@code bulk_job_items} rows behind.
 *
 * <p>{@link BulkJobService#createJob} is {@code @Transactional} and calls {@code
 * publisher.publish(...)} last, after both repository saves (see its own Javadoc and {@link
 * com.artmendez.urlshortener.v2.shortlink.bulk.messaging.BulkJobPublisher}'s: "letting the
 * exception propagate rolls that whole transaction back... no orphaned PENDING row is left behind
 * to retry against"). That is a real, load-bearing correctness claim -- a bulk-job row visible to
 * a caller but never actually queued for bulk-processor to pick up would be a silent bug users
 * would only discover by waiting forever for a job that will never move past {@code PENDING}. A
 * mock-based test can prove the exception reaches the caller; only a real database can prove the
 * rows themselves never committed.
 *
 * <p>Kept as its own test class rather than a method inside {@link BulkJobServiceIntegrationTest},
 * for the exact same reason {@code ShortLinkRedisOutageIntegrationTest} and {@code
 * RateLimiterRedisOutageTest} are each split out from their respective happy-path classes: JUnit
 * does not guarantee method execution order, so stopping this class's shared {@code RabbitMQContainer}
 * mid-test would risk breaking any other test sharing that same static container.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class BulkJobServiceRabbitMqOutageTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

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
    private BulkJobService service;

    @Autowired
    private BulkJobRepository jobRepository;

    @Autowired
    private BulkJobItemRepository itemRepository;

    @Test
    void createJob_rollsBackTheJobAndItsItemsWhenThePublishFails() {
        RABBITMQ.stop();

        List<BulkUrlItemRequest> urls = List.of(new BulkUrlItemRequest("https://example.com/orphan", null));

        // Unlike RateLimiter's Redis Circuit Breaker (which deliberately fails OPEN -- see
        // RateLimiterRedisOutageTest), createJob has no fallback here: BulkJobPublisher's own
        // Javadoc is explicit that a broker outage must fail LOUD, not silently let a job through
        // that nothing will ever process.
        assertThatThrownBy(() -> service.createJob(urls, "user-outage"))
                .isInstanceOf(AmqpException.class);

        assertThat(jobRepository.findAll())
                .as("createJob is @Transactional and publishes last -- a publish failure must roll "
                        + "back the job header row too, not leave it committed with no message ever "
                        + "queued for bulk-processor")
                .isEmpty();
        assertThat(itemRepository.findAll())
                .as("the line items saved earlier in the same transaction must roll back together "
                        + "with the job header, not be left as orphaned rows pointing at a job that "
                        + "was never actually persisted")
                .isEmpty();
    }
}
