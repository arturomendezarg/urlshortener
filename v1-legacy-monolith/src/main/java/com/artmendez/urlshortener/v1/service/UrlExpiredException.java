package com.artmendez.urlshortener.v1.service;

/**
 * Se lanza cuando se resuelve un short code que existe pero cuya fecha de expiracion
 * ({@code expiresAt}) ya paso. Distinta de {@link ShortCodeNotFoundException}: el recurso
 * existio y es identificable, pero ya no es valido (HTTP 410 Gone, no 404).
 */
public class UrlExpiredException extends RuntimeException {

    public UrlExpiredException(String shortCode) {
        super("El short code '" + shortCode + "' expiro");
    }
}
