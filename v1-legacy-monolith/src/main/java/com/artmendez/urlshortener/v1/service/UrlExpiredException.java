package com.artmendez.urlshortener.v1.service;

/**
 * Thrown when resolving a short code that exists but whose expiration date
 * ({@code expiresAt}) has already passed. Different from {@link ShortCodeNotFoundException}:
 * the resource existed and is identifiable, but is no longer valid (HTTP 410 Gone, not 404).
 */
public class UrlExpiredException extends RuntimeException {

    public UrlExpiredException(String shortCode) {
        super("Short code '" + shortCode + "' has expired");
    }
}
