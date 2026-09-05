package com.artmendez.urlshortener.v2.shortlink.bulk.service;

import com.artmendez.urlshortener.v2.shortlink.bulk.domain.BulkJob;
import com.artmendez.urlshortener.v2.shortlink.bulk.domain.BulkJobItem;
import com.artmendez.urlshortener.v2.shortlink.bulk.messaging.BulkJobMessage;
import com.artmendez.urlshortener.v2.shortlink.bulk.repository.BulkJobItemRepository;
import com.artmendez.urlshortener.v2.shortlink.bulk.repository.BulkJobRepository;
import com.artmendez.urlshortener.v2.shortlink.bulk.web.BulkUrlItemRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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

/**
 * Real-infra proof (Day 3, ARCHITECTURE.md section 7's "pruebas de integración con
 * Testcontainers") of {@link BulkJobService#createJob}'s happy path: a real PostgreSQL row for
 * the job header and each line item, plus a real, consumable message on the real {@code
 * bulk-url-jobs} queue.
 *
 * <p>Before this test, {@link BulkJobServiceTest} was the only coverage of {@code createJob} --
 * entirely mock-based ({@code BulkJobRepository}, {@code BulkJobItemRepository} and {@code
 * BulkJobPublisher} are all Mockito mocks there). That proves this service calls its
 * collaborators with the right arguments, but it cannot prove two things only real infrastructure
 * can: that the rows Hibernate builds actually satisfy the {@code bulk_jobs}/{@code
 * bulk_job_items} schema (a column-mapping mistake would pass every mock-based test and still
 * fail against a real database), and that the message this service publishes is one
 * bulk-processor could really receive and deserialize off a real broker.
 *
 * <p>This module's own {@link com.artmendez.urlshortener.v2.shortlink.bulk.messaging.RabbitConfig}
 * deliberately does not declare the {@code bulk-url-jobs} queue (see its Javadoc: "consumer
 * declares" -- only bulk-processor's own {@code RabbitConfig} does that, and bulk-processor is a
 * separate module/Spring context, not part of this one). So this test declares the queue itself,
 * via {@link AmqpAdmin}, purely so a message published during the test has somewhere to land and
 * can be read back with {@link RabbitTemplate#receiveAndConvert(String, long)} -- it is not
 * asserting anything about bulk-processor's own queue/DLQ setup, which is that module's own test
 * suite's job.
 *
 * <p>Uses the same {@code RabbitMQContainer(...).withUser("appuser", "appuser_local")} pattern as
 * {@link com.artmendez.urlshortener.v2.config.KeycloakResourceServerIntegrationTest} and the same
 * reason: RabbitMQ's {@code loopback_users} restriction rejects the default "guest" user
 * authenticating over a Testcontainers-mapped port. {@code WebEnvironment.MOCK} (not {@code
 * NONE}) for the same reason documented on {@link
 * com.artmendez.urlshortener.v2.shortlink.service.ShortLinkServiceIntegrationTest}: {@code NONE}
 * skips the servlet auto-configuration {@code SecurityConfig}'s {@code SecurityFilterChain} bean
 * needs to build, so the context fails to start at all.
 *
 * <p>No Redis or Keycloak containers here (unlike {@code KeycloakResourceServerIntegrationTest}):
 * neither is exercised by anything {@code createJob} touches, and both this module's Redis-backed
 * cache and its OAuth2 resource server config connect lazily on first real use, not at context
 * startup -- {@link
 * com.artmendez.urlshortener.v2.shortlink.service.ShortLinkServiceIntegrationTest} already boots
 * this same full context with no RabbitMQ container present for exactly the symmetric reason.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class BulkJobServiceIntegrationTest {

    private static final String QUEUE_NAME = "bulk-url-jobs";

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

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @BeforeEach
    void declareAndClearTheQueue() {
        // This context never declares "bulk-url-jobs" itself (see class Javadoc) -- declare it
        // here so createJob's publish() has a real queue to land on. Purge first so a message
        // left over from a previous method in this class (same broker, same static @Container)
        // cannot be mistaken for the one this test method itself publishes.
        amqpAdmin.declareQueue(new Queue(QUEUE_NAME, false, false, false));
        amqpAdmin.purgeQueue(QUEUE_NAME, true);
    }

    @Test
    void createJob_persistsTheJobHeaderAndEveryLineItemInPostgres() {
        List<BulkUrlItemRequest> urls = List.of(
                new BulkUrlItemRequest("https://example.com/1", null),
                new BulkUrlItemRequest("https://example.com/2", "custom-alias"));

        BulkJob created = service.createJob(urls, "user-integration-1");

        // Re-read through the repositories instead of trusting the returned object, so this
        // actually proves the row exists in Postgres and not just in the JPA persistence
        // context's in-memory view of what it just wrote.
        BulkJob reloaded = jobRepository.findById(created.getId()).orElseThrow();
        assertThat(reloaded.getOwnerUserId()).isEqualTo("user-integration-1");
        assertThat(reloaded.getTotalItems()).isEqualTo(2);

        List<BulkJobItem> items = itemRepository.findByBulkJobIdOrderByLineIndexAsc(created.getId());
        assertThat(items).hasSize(2);
        assertThat(items.get(0).getLongUrl()).isEqualTo("https://example.com/1");
        assertThat(items.get(0).getCustomAlias()).isNull();
        assertThat(items.get(1).getCustomAlias()).isEqualTo("custom-alias");
    }

    @Test
    void createJob_publishesARealMessageThatBulkProcessorCouldActuallyConsume() {
        BulkJob created = service.createJob(
                List.of(new BulkUrlItemRequest("https://example.com/queued", null)), "user-integration-2");

        // 5s is generous for a message that was already published before this call -- Rabbit
        // delivers same-broker messages effectively immediately; this is not waiting on any
        // asynchronous processing, just a network round trip.
        BulkJobMessage received = (BulkJobMessage) rabbitTemplate.receiveAndConvert(QUEUE_NAME, 5_000);

        assertThat(received).isNotNull();
        assertThat(received.jobId()).isEqualTo(created.getId());
    }
}
