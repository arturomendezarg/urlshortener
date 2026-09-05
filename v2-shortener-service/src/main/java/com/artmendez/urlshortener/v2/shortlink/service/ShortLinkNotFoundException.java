package com.artmendez.urlshortener.v2.shortlink.service;

/** No short link exists for the requested code. Mapped to {@code 404} in the controller. */
public class ShortLinkNotFoundException extends RuntimeException {

    public ShortLinkNotFoundException(String shortCode) {
        super("No short link found for code '" + shortCode + "'");
    }
}
