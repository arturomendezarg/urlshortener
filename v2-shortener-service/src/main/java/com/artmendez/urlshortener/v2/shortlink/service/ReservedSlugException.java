package com.artmendez.urlshortener.v2.shortlink.service;

/**
 * The requested {@code customAlias} is one of the reserved slugs (ARCHITECTURE.md, section 5:
 * "reserved slugs... never assigned as a custom alias"). Mapped to {@code 400} in the
 * controller.
 */
public class ReservedSlugException extends RuntimeException {

    public ReservedSlugException(String slug) {
        super("'" + slug + "' is a reserved slug and cannot be used as a custom alias");
    }
}
