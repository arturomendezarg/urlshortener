package com.artmendez.urlshortener.v2.shortlink.bulk.service;

/**
 * A {@code POST /api/v2/urls/bulk} submission had more lines than
 * {@code app.shortlink.bulk-max-items} allows. Mapped to {@code 400} in the controller.
 */
public class BulkSubmissionTooLargeException extends RuntimeException {

    public BulkSubmissionTooLargeException(int submitted, int max) {
        super("Bulk submission has " + submitted + " items, which exceeds the maximum of " + max);
    }
}
