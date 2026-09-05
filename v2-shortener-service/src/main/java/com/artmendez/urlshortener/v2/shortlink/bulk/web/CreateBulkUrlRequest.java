package com.artmendez.urlshortener.v2.shortlink.bulk.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Request body for {@code POST /api/v2/urls/bulk} (ARCHITECTURE.md, section 4.1). {@code @Valid}
 * on {@code urls} cascades Bean Validation into every {@link BulkUrlItemRequest}; the separate
 * item-count ceiling ({@code app.shortlink.bulk-max-items}) is enforced in
 * {@code BulkJobService}, not here, because {@code @Size}'s bound must be a compile-time
 * constant and this project keeps it externally configurable.
 */
public record CreateBulkUrlRequest(@NotEmpty @Valid List<BulkUrlItemRequest> urls) {

    public CreateBulkUrlRequest {
        urls = urls == null ? null : List.copyOf(urls);
    }
}
