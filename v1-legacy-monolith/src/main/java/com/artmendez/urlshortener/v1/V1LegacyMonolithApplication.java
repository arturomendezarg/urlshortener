package com.artmendez.urlshortener.v1;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the "legacy" Monolith V1.
 *
 * <p>This service simulates a simple pre-existing system: it creates short URLs and redirects.
 * It is intentionally basic (no auth, no cache, naive code generator) to serve as the
 * foundation for the Day 2 Brownfield scenario (see ARCHITECTURE.md, section 6, Scenario B).
 */
@SpringBootApplication
public class V1LegacyMonolithApplication {

    public static void main(String[] args) {
        SpringApplication.run(V1LegacyMonolithApplication.class, args);
    }
}
