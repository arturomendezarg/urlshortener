package com.artmendez.urlshortener.bulk.domain;

/**
 * Mirrors {@code com.artmendez.urlshortener.v2.shortlink.bulk.domain.BulkJobStatus} in
 * v2-shortener-service (same values, same column, same table) — kept as a separate enum rather
 * than a shared one for the same reason {@code analytics-worker}'s {@code ClickEventMessage} is
 * a separate type from V1's {@code ClickEvent}: this module has no compile-time dependency on
 * v2-shortener-service, only on the schema it creates.
 */
public enum BulkJobStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    COMPLETED_WITH_ERRORS,
    FAILED
}
