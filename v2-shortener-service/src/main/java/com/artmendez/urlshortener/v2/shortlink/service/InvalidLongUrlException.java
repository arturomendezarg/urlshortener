package com.artmendez.urlshortener.v2.shortlink.service;

/**
 * {@code longUrl} failed validation: disallowed scheme, or a host rejected to prevent SSRF /
 * open-redirect abuse (ARCHITECTURE.md, section 5). Mapped to {@code 400} in the controller.
 */
public class InvalidLongUrlException extends RuntimeException {

    public InvalidLongUrlException(String reason) {
        super(reason);
    }
}
