package com.artmendez.urlshortener.v1.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Minimal RabbitMQ configuration for V1.
 *
 * <p>Deliberately does NOT declare the {@code click-events} queue (no {@code Queue} bean or
 * explicit {@code RabbitAdmin}). If V1 declared the queue, Spring AMQP would try to connect to
 * the broker at context startup ({@code ContextRefreshedEvent}) to declare it — which would
 * break all the existing tests that do not spin up a RabbitMQ container (Postgres only). In
 * this design, the producer only knows the queue's NAME; declaring it is the consumer's
 * responsibility (Analytics Worker, Task #7), which does need it to exist before it can listen.
 * The {@code RabbitTemplate} connection is lazy: it only opens on the first
 * {@code convertAndSend}, never at startup.
 */
@Configuration
public class RabbitConfig {

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
