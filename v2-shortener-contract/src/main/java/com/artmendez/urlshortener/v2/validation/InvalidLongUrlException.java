package com.artmendez.urlshortener.v2.validation;

/**
 * {@code longUrl} failed validation: disallowed scheme, or a host rejected to prevent SSRF /
 * open-redirect abuse (ARCHITECTURE.md, section 5). Mapped to {@code 400} by whichever service
 * calls {@link LongUrlValidator} (see {@code ShortLinkController} in v2-shortener-service).
 */
public class InvalidLongUrlException extends RuntimeException {

    public InvalidLongUrlException(String reason) {
        super(reason);
    }
}
