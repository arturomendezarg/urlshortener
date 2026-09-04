package com.artmendez.urlshortener.v1;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del Monolito V1 "legacy".
 *
 * <p>Este servicio simula un sistema pre-existente simple: crea URLs cortas y redirige.
 * Es intencionalmente basico (sin auth, sin cache, generador de codigo naive) para servir
 * como base del escenario Brownfield del Dia 2 (ver ARCHITECTURE.md, seccion 6, Escenario B).
 */
@SpringBootApplication
public class V1LegacyMonolithApplication {

    public static void main(String[] args) {
        SpringApplication.run(V1LegacyMonolithApplication.class, args);
    }
}
