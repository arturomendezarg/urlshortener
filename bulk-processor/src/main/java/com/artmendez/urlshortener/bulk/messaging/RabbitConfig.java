package com.artmendez.urlshortener.bulk.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

/**
 * RabbitMQ configuration for the {@code bulk-url-jobs} CONSUMER side (Day 3, Task #10).
 *
 * <p>Unlike v2-shortener-service's producer-side {@code RabbitConfig} (which deliberately does
 * not declare the queue — same "producer stays queue-agnostic, consumer declares" split used
 * for {@code click-events}), this consumer declares the main queue AND its full dead-letter
 * setup, since it is the one that needs all of it to exist before it can start listening.
 *
 * <p>Two independent layers of dead-lettering work together here, covering two different
 * failure points:
 * <ul>
 *   <li>The main queue's {@code x-dead-letter-exchange} argument (native RabbitMQ
 *       dead-lettering) catches a message the container rejects WITHOUT ever reaching
 *       {@link BulkJobListener#handle}, e.g. one that fails JSON deserialization — the retry
 *       interceptor below never even sees those, since it wraps the listener invocation, not
 *       message conversion. {@link #rabbitListenerContainerFactory} sets
 *       {@code defaultRequeueRejected(false)} so such a rejection dead-letters instead of
 *       looping forever.
 *   <li>{@link #bulkJobRetryInterceptor}, Spring Retry's bounded local retry around the
 *       listener method itself, for exceptions {@link
 *       com.artmendez.urlshortener.bulk.service.BulkJobProcessingService} lets propagate
 *       (genuine infrastructure failures — see its Javadoc). After {@code max-attempts} tries,
 *       {@link RepublishMessageRecoverer} explicitly republishes the message to the SAME
 *       dead-letter exchange/queue, rather than relying on RabbitMQ's native mechanism a second
 *       time.
 * </ul>
 */
@Configuration
public class RabbitConfig {

    @Bean
    public Queue bulkUrlJobsQueue(
            @Value("${app.messaging.bulk-url-jobs-queue}") String queueName,
            @Value("${app.messaging.bulk-url-jobs-dlx}") String dlxName) {
        return QueueBuilder.durable(queueName)
                .withArgument("x-dead-letter-exchange", dlxName)
                .build();
    }

    @Bean
    public DirectExchange bulkUrlJobsDlx(@Value("${app.messaging.bulk-url-jobs-dlx}") String dlxName) {
        return new DirectExchange(dlxName, true, false);
    }

    @Bean
    public Queue bulkUrlJobsDlq(@Value("${app.messaging.bulk-url-jobs-dlq}") String dlqName) {
        return QueueBuilder.durable(dlqName).build();
    }

    @Bean
    public Binding bulkUrlJobsDlqBinding(
            Queue bulkUrlJobsDlq,
            DirectExchange bulkUrlJobsDlx,
            @Value("${app.messaging.bulk-url-jobs-queue}") String mainQueueName) {
        // A message dead-lettered by RabbitMQ itself keeps its original routing key, which for
        // a message published (like v2-shortener-service does) through the default exchange
        // with routingKey = queue name IS that queue's name. RepublishMessageRecoverer below is
        // told to use the same routing key so both dead-lettering paths land in this one DLQ.
        return BindingBuilder.bind(bulkUrlJobsDlq).to(bulkUrlJobsDlx).with(mainQueueName);
    }

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        // REQUIRED here, not just a nicety: v2-shortener-service's message class
        // (...shortlink.bulk.messaging.BulkJobMessage) is not on this module's classpath at
        // all, so resolving the sender's __TypeId__ header would throw ClassNotFoundException.
        // This makes the converter deserialize straight into BulkJobListener's declared
        // parameter type instead (same fix as analytics-worker's RabbitConfig, for the same
        // underlying reason).
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        converter.setAlwaysConvertToInferredType(true);
        return converter;
    }

    @Bean
    public RetryOperationsInterceptor bulkJobRetryInterceptor(
            AmqpTemplate amqpTemplate,
            @Value("${app.messaging.bulk-url-jobs-dlx}") String dlxName,
            @Value("${app.messaging.bulk-url-jobs-queue}") String mainQueueName,
            @Value("${app.messaging.retry.max-attempts}") int maxAttempts,
            @Value("${app.messaging.retry.initial-interval-ms}") long initialIntervalMs,
            @Value("${app.messaging.retry.multiplier}") double multiplier,
            @Value("${app.messaging.retry.max-interval-ms}") long maxIntervalMs) {
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(initialIntervalMs);
        backOffPolicy.setMultiplier(multiplier);
        backOffPolicy.setMaxInterval(maxIntervalMs);
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(maxAttempts)
                .backOffPolicy(backOffPolicy)
                .recoverer(new RepublishMessageRecoverer(amqpTemplate, dlxName, mainQueueName))
                .build();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter,
            RetryOperationsInterceptor bulkJobRetryInterceptor) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(bulkJobRetryInterceptor);
        return factory;
    }
}
