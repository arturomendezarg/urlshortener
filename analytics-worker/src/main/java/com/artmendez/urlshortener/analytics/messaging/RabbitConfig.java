package com.artmendez.urlshortener.analytics.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration for the Analytics Worker.
 *
 * <p>Unlike the producer (V1's {@code RabbitConfig}, which deliberately does not declare the
 * queue), this consumer DOES declare the {@code click-events} queue as a {@link Queue} bean, so
 * that Spring AMQP's auto-configured {@code RabbitAdmin} creates it at startup if it does not
 * already exist. This is the split of responsibility documented in
 * {@code ClickEventPublisher}: "producer stays queue-agnostic, consumer declares" — the queue
 * must exist before this worker can start listening to it.
 *
 * <p>{@link Jackson2JsonMessageConverter#setAlwaysConvertToInferredType(boolean)} is set to
 * {@code true} on purpose: by default this converter resolves the target Java type from the
 * message's {@code __TypeId__} header, which Spring AMQP fills in with the SENDER's fully
 * qualified class name (here, {@code com.artmendez.urlshortener.v1.messaging.ClickEvent} — a
 * class this module does not have on its classpath, and should not depend on). Enabling
 * "always convert to inferred type" makes the converter ignore that header entirely and
 * deserialize straight into the parameter type declared on the {@code @RabbitListener} method
 * ({@link ClickEventMessage}) instead — the correct, decoupled behavior for a queue meant to be
 * fed by more than one producer (V1 today, V2 later) that do not share a Java module.
 */
@Configuration
public class RabbitConfig {

    @Bean
    public Queue clickEventsQueue(@Value("${app.messaging.click-events-queue}") String queueName) {
        return new Queue(queueName, true);
    }

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        converter.setAlwaysConvertToInferredType(true);
        return converter;
    }
}
