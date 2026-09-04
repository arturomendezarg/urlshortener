package com.artmendez.urlshortener.v1.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Publica eventos de clic a RabbitMQ de forma "fire-and-forget" (ver ARCHITECTURE.md, seccion 8,
 * tabla de riesgos: "Perdida silenciosa de eventos de clic si RabbitMQ esta caido al publicar" —
 * riesgo aceptado explicitamente, no un descuido).
 *
 * <p>Contrato explicito: {@link #publish(ClickEvent)} NUNCA propaga una excepcion. Si el broker
 * esta caido, inalcanzable, o cualquier otro fallo de mensajeria ocurre, se registra un warning
 * y se retorna normalmente. La redireccion al usuario (el camino critico) no debe depender en
 * absoluto de la disponibilidad de RabbitMQ.
 */
@Component
public class ClickEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ClickEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final String queueName;

    public ClickEventPublisher(
            RabbitTemplate rabbitTemplate,
            @Value("${app.messaging.click-events-queue}") String queueName) {
        this.rabbitTemplate = rabbitTemplate;
        this.queueName = queueName;
    }

    public void publish(ClickEvent event) {
        try {
            // Exchange por defecto ("") con routing key = nombre de la cola: no requiere que V1
            // declare ni conozca ningun exchange, solo el nombre acordado de la cola.
            rabbitTemplate.convertAndSend(queueName, event);
        } catch (Exception ex) {
            log.warn(
                    "No se pudo publicar el evento de clic para el short code '{}' "
                            + "(fire-and-forget, la redireccion continua normalmente): {}",
                    event.shortCode(),
                    ex.getMessage());
        }
    }
}
