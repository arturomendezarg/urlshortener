package com.artmendez.urlshortener.v2.codec;

import java.security.SecureRandom;
import java.util.function.Predicate;

/**
 * Base62 short code generator for the V2 system, with more robust collision handling
 * than the simple generator in Monolith V1 (see
 * {@code com.artmendez.urlshortener.v1.service.UrlShortenerService}, deliberately naive).
 *
 * <p>Strategy (see ARCHITECTURE.md, section 6, Scenario A, point 3): for each length,
 * from {@link #INITIAL_LENGTH} to {@link #MAX_LENGTH}, up to
 * {@link #MAX_ATTEMPTS_PER_LENGTH} random codes are attempted. If all of them collide, it
 * moves on to the next length (fallback to a longer length) instead of retrying indefinitely at
 * the same length, which drives the collision probability toward zero as the
 * search space grows.
 *
 * <p>It does not depend on a concrete repository: it receives the collision check as a
 * {@link Predicate}, so it can be tested without a database or Redis (which are integrated on
 * Day 2, when this generator is wired up to the real persistence of the Shortener Service V2).
 */
public class Base62CodeGenerator {

    private static final String ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int INITIAL_LENGTH = 7;
    private static final int MAX_LENGTH = 12;
    private static final int MAX_ATTEMPTS_PER_LENGTH = 5;

    private final SecureRandom random = new SecureRandom();

    /**
     * Generates a Base62 code that does not exist according to {@code codeExists}.
     *
     * @param codeExists predicate that returns {@code true} if the candidate code is already in
     *                   use (e.g. {@code repository::existsByShortCode})
     * @return a free code, between {@link #INITIAL_LENGTH} and {@link #MAX_LENGTH} characters
     * @throws CodeGenerationExhaustedException if attempts are exhausted at every length
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
