package com.artmendez.urlshortener.v1.service;

/**
 * Thrown when a short code that does not exist in the system is requested.
 * Translated to HTTP 404 in {@link com.artmendez.urlshortener.v1.web.UrlController}.
 */
public class ShortCodeNotFoundException extends RuntimeException {

    public ShortCodeNotFoundException(String shortCode) {
        super("No URL is associated with code: " + shortCode);
    }
}
