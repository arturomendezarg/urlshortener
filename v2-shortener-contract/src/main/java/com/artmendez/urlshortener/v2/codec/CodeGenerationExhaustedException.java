package com.artmendez.urlshortener.v2.codec;

/**
 * Se lanza cuando {@link Base62CodeGenerator} agota todos los intentos en todas las longitudes
 * permitidas sin encontrar un codigo libre. En condiciones normales esto es virtualmente
 * imposible (el espacio de codigos crece exponencialmente con la longitud); si ocurre en
 * produccion, es señal de un bug en la verificacion de colisiones (p. ej. siempre devuelve
 * true) mas que de agotamiento real del espacio de codigos.
 */
public class CodeGenerationExhaustedException extends RuntimeException {

    public CodeGenerationExhaustedException(int maxLength, int maxAttemptsPerLength) {
        super("No se pudo generar un codigo Base62 libre tras agotar " + maxAttemptsPerLength
                + " intentos en cada longitud hasta " + maxLength + " caracteres");
    }
}
