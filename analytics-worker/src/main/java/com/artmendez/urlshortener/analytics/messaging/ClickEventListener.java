package com.artmendez.urlshortener.analytics.messaging;

import com.artmendez.urlshortener.analytics.service.ClickEventProcessingService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Listens to the {@code click-events} queue and delegates each message to
 * {@link ClickEventProcessingService}.
 *
 * <p>Unlike the producer side (V1's {@code ClickEventPublisher}, which must never let a
 * messaging failure affect the redirect response), this consumer deliberately lets processing
 * exceptions propagate out of the listener method. Spring AMQP's default container behavior on
 * an uncaught exception is to reject and requeue the message (basic-nack, no dead-letter queue
 * configured yet) rather than silently drop it — acceptable for this task's scope. A proper
 * retry-limit and dead-letter-queue policy is explicitly in scope for the Bulk Processor's
 * messaging work on Day 3 (see ARCHITECTURE.md, section 7); the same pattern can be applied
 * here once that policy exists.
 */
@Component
public class ClickEventListener {

    private static final Logger log = LoggerFactory.getLogger(ClickEventListener.class);

    private final ClickEventProcessingService processingService;

    public ClickEventListener(ClickEventProcessingService processingService) {
        this.processingService = processingService;
    }

    @RabbitListener(queues = "${app.messaging.click-events-queue}")
    public void handle(ClickEventMessage message) {
        processingService.process(message);
        log.debug("Persisted click event for short code '{}' (origin: {})", message.shortCode(), message.serviceOrigin());
    }
}
