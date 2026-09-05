package com.artmendez.urlshortener.v2.shortlink.service;

/**
 * The requested {@code customAlias} is already in use by another short link. Mapped to
 * {@code 409} in the controller, per the OpenAPI contract.
 */
public class DuplicateAliasException extends RuntimeException {

    public DuplicateAliasException(String alias) {
        super("customAlias '" + alias + "' is already in use");
    }
}
