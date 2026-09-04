package com.artmendez.urlshortener.v1.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Publishes click events to RabbitMQ in a "fire-and-forget" fashion (see ARCHITECTURE.md,
 * section 8, risk table: "Silent loss of click events if RabbitMQ is down when publishing" —
 * an explicitly accepted risk, not an oversight).
 *
 * <p>Explicit contract: {@link #publish(ClickEvent)} NEVER propagates an exception. If the
 * broker is down, unreachable, or any other messaging failure occurs, a warning is logged and
 * the method returns normally. The redirect to the user (the critical path) must not depend
 * on RabbitMQ's availability in any way.
 */
@Component
public class ClickEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ClickEventPublisher.class);

    private final AmqpTemplate amqpTemplate;
    private final String queueName;

    // We depend on the AmqpTemplate interface (not the concrete RabbitTemplate class): Spring
    // still injects the auto-configured RabbitTemplate (it implements AmqpTemplate), and coding
    // against the interface avoids the SpotBugs EI_EXPOSE_REP2 finding ("may expose internal
    // representation") triggered by storing a mutable concrete class received via the
    // constructor - the same reason the Spring Data repositories (interfaces) in other classes
    // of this project never trigger it.
    public ClickEventPublisher(
            AmqpTemplate amqpTemplate,
            @Value("${app.messaging.click-events-queue}") String queueName) {
        this.amqpTemplate = amqpTemplate;
        this.queueName = queueName;
    }

    public void publish(ClickEvent event) {
        try {
            // Default exchange ("") with routing key = queue name: this does not require V1 to
            // declare or know about any exchange, only the agreed-upon queue name.
            amqpTemplate.convertAndSend(queueName, event);
        } catch (Exception ex) {
            log.warn(
                    "Could not publish the click event for short code '{}' "
                            + "(fire-and-forget, the redirect continues normally): {}",
                    event.shortCode(),
                    ex.getMessage());
        }
    }
}
