package com.artmendez.urlshortener.bulk.messaging;

import com.artmendez.urlshortener.bulk.service.BulkJobProcessingService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Listens to {@code bulk-url-jobs} and delegates to {@link BulkJobProcessingService}.
 *
 * <p>Unlike {@code analytics-worker}'s {@code ClickEventListener} (which relies on the
 * container's default reject-and-requeue-forever behavior — a policy explicitly deferred to
 * this task, per that class's own Javadoc), this listener's container factory
 * ({@code RabbitConfig.rabbitListenerContainerFactory}) is wrapped with a bounded Spring Retry
 * interceptor: an uncaught exception here is retried locally a few times, and only republished
 * to the {@code bulk-url-jobs.dlq} dead-letter queue once those retries are exhausted, instead
 * of looping forever or being silently dropped.
 */
@Component
public class BulkJobListener {

    private static final Logger log = LoggerFactory.getLogger(BulkJobListener.class);

    private final BulkJobProcessingService processingService;

    public BulkJobListener(BulkJobProcessingService processingService) {
        this.processingService = processingService;
    }

    @RabbitListener(queues = "${app.messaging.bulk-url-jobs-queue}")
    public void handle(BulkJobMessage message) {
        log.debug("Processing bulk job {}", message.jobId());
        processingService.process(message.jobId());
    }
}
