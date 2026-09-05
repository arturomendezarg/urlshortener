package com.artmendez.urlshortener.v2.shortlink.bulk.domain;

/** Per-line outcome of a {@link BulkJobItem}. Terminal states are never revisited. */
public enum BulkJobItemStatus {
    PENDING,
    COMPLETED,
    FAILED
}
