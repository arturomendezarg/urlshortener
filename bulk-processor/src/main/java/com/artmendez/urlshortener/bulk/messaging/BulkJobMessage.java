package com.artmendez.urlshortener.bulk.messaging;

/**
 * Consumer-side shape of the message v2-shortener-service publishes to {@code bulk-url-jobs}.
 * Deliberately a separate type from that service's own
 * {@code ...shortlink.bulk.messaging.BulkJobMessage} record — same rationale as
 * analytics-worker's {@code ClickEventMessage} vs. V1's {@code ClickEvent} (see this module's
 * {@code RabbitConfig}: this consumer never trusts the producer's Java class name to
 * deserialize).
 */
public record BulkJobMessage(Long jobId) {
}
