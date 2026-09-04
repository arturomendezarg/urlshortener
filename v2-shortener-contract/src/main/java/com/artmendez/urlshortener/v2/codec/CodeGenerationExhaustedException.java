package com.artmendez.urlshortener.v2.codec;

/**
 * Thrown when {@link Base62CodeGenerator} exhausts all attempts at every allowed
 * length without finding a free code. Under normal conditions this is virtually
 * impossible (the code space grows exponentially with length); if it happens in
 * production, it is a sign of a bug in the collision check (e.g. it always returns
 * true) rather than genuine exhaustion of the code space.
 */
public class CodeGenerationExhaustedException extends RuntimeException {

    public CodeGenerationExhaustedException(int maxLength, int maxAttemptsPerLength) {
        super("Could not generate a free Base62 code after exhausting " + maxAttemptsPerLength
                + " attempts at each length up to " + maxLength + " characters");
    }
}
