package com.artmendez.urlshortener.v2.shortlink.bulk.domain;

/**
 * Lifecycle of a {@link BulkJob} (ARCHITECTURE.md, section 4.1/4.2, Day 3 Task #10).
 * v2-shortener-service only ever writes {@link #PENDING} (at job creation); every other
 * transition is written by bulk-processor as it consumes the job from the bulk-url-jobs queue.
 */
public enum BulkJobStatus {
    /** Job row created, message published, no item processed yet. */
    PENDING,
    /** bulk-processor has started working through the job's items. */
    PROCESSING,
    /** Every item succeeded. */
    COMPLETED,
    /** At least one item succeeded and at least one failed. */
    COMPLETED_WITH_ERRORS,
    /** Every item failed. */
    FAILED
}
