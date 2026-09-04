package com.artmendez.urlshortener.v2.shortlink.bulk.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Minimal RabbitMQ configuration for this service's bulk-job PRODUCER side. Deliberately does
 * NOT declare the {@code bulk-url-jobs} queue (no {@code Queue} bean) — same split of
 * responsibility as V1's {@code RabbitConfig} for {@code click-events}: the consumer
 * (bulk-processor) declares the queue (and its dead-letter setup), because it is the one that
 * needs it to exist before it can start listening.
 */
@Configuration
public class RabbitConfig {

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
