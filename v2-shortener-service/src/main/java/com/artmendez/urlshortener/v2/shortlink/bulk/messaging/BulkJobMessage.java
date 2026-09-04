package com.artmendez.urlshortener.v2.shortlink.bulk.messaging;

/**
 * Message published to the {@code bulk-url-jobs} queue after a bulk job's header and items are
 * committed to PostgreSQL. Deliberately carries only {@code jobId}, not the item payload itself:
 * bulk-processor reloads the job's items fresh from the database before processing, which is
 * what makes redelivery of this message safe to handle idempotently (re-reading current item
 * status rather than trusting a possibly-stale copy carried in the message body).
 */
public record BulkJobMessage(Long jobId) {
}
