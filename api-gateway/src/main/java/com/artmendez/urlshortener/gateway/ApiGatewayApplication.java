package com.artmendez.urlshortener.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Single entry point of the system (Strangler Fig pattern, see ARCHITECTURE.md section 3.1):
 * routes the control plane (/api/v1/**, /api/v2/**) and the public data plane
 * (GET /{shortCode}) to the corresponding backend.
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
