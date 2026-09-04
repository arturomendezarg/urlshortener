package com.artmendez.urlshortener.v2.shortlink.bulk.web;

import java.util.List;

/** Response body for {@code GET /api/v2/urls/bulk/{jobId}} (ARCHITECTURE.md, section 4.1). */
public record BulkJobStatusResponse(
        Long jobId, String status, int totalItems, int processedItems, int failedItems,
        List<BulkJobItemResponse> items) {

    public BulkJobStatusResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
