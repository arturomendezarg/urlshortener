package com.artmendez.urlshortener.v2.shortlink.bulk.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * One line of {@code POST /api/v2/urls/bulk}'s {@code urls} array (ARCHITECTURE.md, section
 * 4.1). Same {@code customAlias} shape as {@code CreateUrlRequest} (single-URL create).
 */
public record BulkUrlItemRequest(
        @NotBlank String longUrl,
        @Pattern(regexp = "^[0-9A-Za-z_-]{3,20}$") String customAlias) {
}
