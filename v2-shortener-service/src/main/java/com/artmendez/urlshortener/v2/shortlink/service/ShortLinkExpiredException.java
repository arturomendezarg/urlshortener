package com.artmendez.urlshortener.v2.shortlink.service;

/**
 * The short link exists but is past its {@code expiresAt} or has been deactivated. Mapped to
 * {@code 410 Gone} in the controller (ARCHITECTURE.md, section 6, Scenario C: "expire well").
 */
public class ShortLinkExpiredException extends RuntimeException {

    public ShortLinkExpiredException(String shortCode) {
        super("Short link '" + shortCode + "' is expired or inactive");
    }
}
