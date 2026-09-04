package com.artmendez.urlshortener.analytics.messaging;

import java.time.OffsetDateTime;

/**
 * Consumer-side shape of the click event published to the RabbitMQ {@code click-events} queue.
 *
 * <p>This record is deliberately declared independently from the producer's own {@code
 * ClickEvent} (in {@code v1-legacy-monolith}), rather than shared through a common module. The
 * field names and types are kept identical on purpose so the JSON payload deserializes
 * correctly, but the two types are not the same class: see {@link RabbitConfig} for why this
 * consumer never relies on the producer's Java class name to deserialize.
 *
 * <p>{@code deviceType} is intentionally absent here too: no producer (V1 today, V2 later)
 * populates it, so it is not part of the wire contract. This worker infers it itself from
 * {@code userAgent} before persisting (see {@link com.artmendez.urlshortener.analytics.service.DeviceTypeClassifier}).
 */
public record ClickEventMessage(
        String shortCode,
        String serviceOrigin,
        OffsetDateTime occurredAt,
        String clientIp,
        String userAgent,
        String referrer) {
}
