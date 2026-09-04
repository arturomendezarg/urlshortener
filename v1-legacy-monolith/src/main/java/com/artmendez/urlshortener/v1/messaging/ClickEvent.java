package com.artmendez.urlshortener.v1.messaging;

import java.time.OffsetDateTime;

/**
 * Click event published to the RabbitMQ queue {@code click-events} (see ARCHITECTURE.md,
 * section 4, table {@code click_events}).
 *
 * <p>Deliberate note: {@code clientIp} travels UNANONYMIZED. Anonymization of the last octet
 * happens in the Analytics Worker (Task #7), right before inserting into {@code click_events} —
 * this event is the raw input data for that pipeline, not the final persisted record.
 *
 * <p>{@code deviceType} is not computed in V1 (simple code generator, without the per-device
 * conditional redirect logic reserved for Scenario C / V2); it is left out of this event
 * instead of being sent as a guessed {@code null}, and the Analytics Worker decides how to
 * fill it in for events originating from V1.
 */
public record ClickEvent(
        String shortCode,
        String serviceOrigin,
        OffsetDateTime occurredAt,
        String clientIp,
        String userAgent,
        String referrer) {
}
