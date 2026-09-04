package com.artmendez.urlshortener.v2.codec;

import java.security.SecureRandom;
import java.util.function.Predicate;

/**
 * Generador de codigos cortos Base62 para el sistema V2, con manejo de colisiones mas robusto
 * que el generador simple del Monolito V1 (ver
 * {@code com.artmendez.urlshortener.v1.service.UrlShortenerService}, deliberadamente naive).
 *
 * <p>Estrategia (ver ARCHITECTURE.md, seccion 6, Escenario A, punto 3): para cada longitud,
 * de {@link #INITIAL_LENGTH} a {@link #MAX_LENGTH}, se intentan hasta
 * {@link #MAX_ATTEMPTS_PER_LENGTH} codigos aleatorios. Si todos colisionan, se pasa a la
 * siguiente longitud (fallback a mayor longitud) en vez de reintentar indefinidamente a la
 * misma longitud, lo cual acerca la probabilidad de colision a cero a medida que crece el
 * espacio de busqueda.
 *
 * <p>No depende de un repositorio concreto: recibe la verificacion de colisiones como un
 * {@link Predicate}, para poder probarse sin base de datos ni Redis (que se integran en el
 * Dia 2, cuando este generador se conecte a la persistencia real del Shortener Service V2).
 */
public class Base62CodeGenerator {

    private static final String ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int INITIAL_LENGTH = 7;
    private static final int MAX_LENGTH = 12;
    private static final int MAX_ATTEMPTS_PER_LENGTH = 5;

    private final SecureRandom random = new SecureRandom();

    /**
     * Genera un codigo Base62 que no existe segun {@code codeExists}.
     *
     * @param codeExists predicado que devuelve {@code true} si el codigo candidato ya está en
     *                   uso (p. ej. {@code repository::existsByShortCode})
     * @return un codigo libre, de entre {@link #INITIAL_LENGTH} y {@link #MAX_LENGTH} caracteres
     * @throws CodeGenerationExhaustedException si se agotan los intentos en todas las longitudes
     */
    public String generate(Predicate<String> codeExists) {
        for (int length = INITIAL_LENGTH; length <= MAX_LENGTH; length++) {
            for (int attempt = 0; attempt < MAX_ATTEMPTS_PER_LENGTH; attempt++) {
                String candidate = randomCode(length);
                if (!codeExists.test(candidate)) {
                    return candidate;
                }
            }
        }
        throw new CodeGenerationExhaustedException(MAX_LENGTH, MAX_ATTEMPTS_PER_LENGTH);
    }

    private String randomCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
