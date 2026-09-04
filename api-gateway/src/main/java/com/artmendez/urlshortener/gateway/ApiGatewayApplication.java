package com.artmendez.urlshortener.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada unico del sistema (patron Strangler Fig, ver ARCHITECTURE.md seccion 3.1):
 * enruta el plano de control (/api/v1/**, /api/v2/**) y el plano de datos publico
 * (GET /{shortCode}) hacia el backend correspondiente.
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
