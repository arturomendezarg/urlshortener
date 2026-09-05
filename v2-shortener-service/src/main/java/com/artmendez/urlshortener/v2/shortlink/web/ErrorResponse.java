package com.artmendez.urlshortener.v2.shortlink.web;

import java.time.OffsetDateTime;

/**
 * Error body for the create endpoint's {@code 400}/{@code 409} responses, matching
 * {@code ErrorResponse} in the OpenAPI contract. {@code GET /{shortCode}} does not use this: it
 * is a public redirect endpoint outside the {@code /api/v2} contract (see the contract's own
 * "out of scope" note), so it returns plain {@code 404}/{@code 410} with no body, matching
 * {@code v1-legacy-monolith}'s existing redirect endpoint.
 */
public record ErrorResponse(OffsetDateTime timestamp, int status, String error, String message, String path) {
}
