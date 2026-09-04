package com.artmendez.urlshortener.v2.shortlink.web;

/**
 * Response body for {@code POST /api/v2/urls}, matching {@code CreateUrlResponse} in the
 * OpenAPI contract.
 */
public record CreateUrlResponse(String shortCode, String shortUrl, String ownerId) {
}
