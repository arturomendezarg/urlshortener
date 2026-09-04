package com.artmendez.urlshortener.bulk.service;

/**
 * A single bulk item failed BUSINESS validation (reserved slug, alias already taken) — as
 * opposed to an infrastructure failure. Always caught locally in
 * {@link BulkJobProcessingService}, never allowed to propagate: it marks one item {@code
 * FAILED} and processing continues with the next line. There is no HTTP layer here to map this
 * to a status code (unlike the equivalent exceptions in v2-shortener-service); its message IS
 * the persisted {@code error_message} shown back to the caller via
 * {@code GET /api/v2/urls/bulk/{jobId}}.
 */
public class BulkItemProcessingException extends RuntimeException {

    public BulkItemProcessingException(String message) {
        super(message);
    }
}
