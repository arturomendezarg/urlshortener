package com.artmendez.urlshortener.v1.messaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.net.ConnectException;
import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ClickEventPublisherTest {

    private static final String QUEUE_NAME = "click-events";

    @Mock
    private RabbitTemplate rabbitTemplate;

    private ClickEventPublisher publisher() {
        return new ClickEventPublisher(rabbitTemplate, QUEUE_NAME);
    }

    private ClickEvent sampleEvent() {
        return new ClickEvent(
                "abc123",
                "v1",
                OffsetDateTime.now(),
                "203.0.113.7",
                "Mozilla/5.0",
                "https://example.com/referrer");
    }

    @Test
    void publish_sendsEventToConfiguredQueue() {
        ClickEvent event = sampleEvent();

        publisher().publish(event);

        verify(rabbitTemplate).convertAndSend(eq(QUEUE_NAME), eq(event));
    }

    @Test
    void publish_swallowsExceptionWhenBrokerIsUnreachable() {
        doThrow(new AmqpConnectException(new ConnectException("Connection refused")))
                .when(rabbitTemplate).convertAndSend(any(String.class), any(Object.class));

        // Must not throw: this is the core "fire-and-forget" contract.
        publisher().publish(sampleEvent());

        verify(rabbitTemplate).convertAndSend(eq(QUEUE_NAME), any(ClickEvent.class));
    }

    @Test
    void publish_swallowsAnyRuntimeException_notJustAmqpSpecific() {
        doThrow(new RuntimeException("unexpected serialization failure"))
                .when(rabbitTemplate).convertAndSend(any(String.class), any(Object.class));

        publisher().publish(sampleEvent());

        verify(rabbitTemplate).convertAndSend(eq(QUEUE_NAME), any(ClickEvent.class));
    }
}
