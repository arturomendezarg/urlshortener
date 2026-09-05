package com.artmendez.urlshortener.v2.shortlink.bulk.web;

/** Response body for {@code POST /api/v2/urls/bulk} (ARCHITECTURE.md, section 4.1). */
public record CreateBulkUrlResponse(Long jobId, String status, int totalItems) {
}
