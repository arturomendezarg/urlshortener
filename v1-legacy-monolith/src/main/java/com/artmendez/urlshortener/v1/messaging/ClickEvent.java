package com.artmendez.urlshortener.v1.messaging;

import java.time.OffsetDateTime;

/**
 * Evento de clic publicado a la cola RabbitMQ {@code click-events} (ver ARCHITECTURE.md,
 * seccion 4, tabla {@code click_events}).
 *
 * <p>Nota deliberada: {@code clientIp} viaja SIN anonimizar. La anonimizacion del ultimo octeto
 * ocurre en el Analytics Worker (Tarea #7), justo antes de insertar en {@code click_events} —
 * este evento es el dato crudo de entrada a ese pipeline, no el registro final persistido.
 *
 * <p>{@code deviceType} no se calcula en V1 (generador de codigo simple, sin la logica de
 * redireccion condicional por dispositivo reservada para el Escenario C / V2); se deja fuera
 * de este evento en vez de enviarlo como {@code null} adivinado, y el Analytics Worker decide
 * como completarlo para eventos de origen V1.
 */
public record ClickEvent(
        String shortCode,
        String serviceOrigin,
        OffsetDateTime occurredAt,
        String clientIp,
        String userAgent,
        String referrer) {
}
