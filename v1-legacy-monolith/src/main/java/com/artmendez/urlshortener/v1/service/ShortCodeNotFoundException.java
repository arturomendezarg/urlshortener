package com.artmendez.urlshortener.v1.service;

/**
 * Se lanza cuando se solicita un short code que no existe en el sistema.
 * Traducida a HTTP 404 en {@link com.artmendez.urlshortener.v1.web.UrlController}.
 */
public class ShortCodeNotFoundException extends RuntimeException {

    public ShortCodeNotFoundException(String shortCode) {
        super("No existe una URL asociada al codigo: " + shortCode);
    }
}
