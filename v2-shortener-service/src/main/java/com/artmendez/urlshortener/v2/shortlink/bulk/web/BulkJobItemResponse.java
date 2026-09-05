package com.artmendez.urlshortener.v2.shortlink.bulk.web;

/** One line item inside {@link BulkJobStatusResponse}. */
public record BulkJobItemResponse(
        int lineIndex, String longUrl, String status, String shortCode, String errorMessage) {
}
