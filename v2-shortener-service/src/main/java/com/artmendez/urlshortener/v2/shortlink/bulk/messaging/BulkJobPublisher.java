package com.artmendez.urlshortener.v2.shortlink.bulk.messaging;

import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Publishes to the {@code bulk-url-jobs} queue.
 *
 * <p>Unlike V1's {@code ClickEventPublisher} (fire-and-forget: click analytics are allowed to
 * be silently lost per ARCHITECTURE.md section 8's risk table), a lost bulk-job message means a
 * job stuck at {@code PENDING} forever with no visible error to the caller — worse than a loud
 * failure. So this method deliberately does NOT catch messaging exceptions: it is only ever
 * called from {@code BulkJobService.createJob}, inside the same {@code @Transactional} boundary
 * that persisted the job and its items, so letting the exception propagate rolls that whole
 * transaction back — the caller gets a clear error and no orphaned {@code PENDING} row is left
 * behind to retry against.
 */
@Component
public class BulkJobPublisher {

    private final AmqpTemplate amqpTemplate;
    private final String queueName;

    public BulkJobPublisher(
            AmqpTemplate amqpTemplate, @Value("${app.messaging.bulk-url-jobs-queue}") String queueName) {
        this.amqpTemplate = amqpTemplate;
        this.queueName = queueName;
    }

    public void publish(BulkJobMessage message) {
        amqpTemplate.convertAndSend(queueName, message);
    }
}
