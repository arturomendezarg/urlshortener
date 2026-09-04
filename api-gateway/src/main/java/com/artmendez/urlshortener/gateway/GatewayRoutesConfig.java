package com.artmendez.urlshortener.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

/**
 * Gateway routes (basic version, Day 1 / Task #3 of the plan).
 *
 * <p>Two active routes:
 * <ul>
 *   <li>{@code /api/v1/**} (control plane) -> Monolith V1, as-is.</li>
 *   <li>{@code GET /{shortCode}} (public data plane, no prefix) -> Monolith V1.
 *       Note: the final version of this route (ARCHITECTURE.md section 3.1) must first
 *       check the V2 code index in Redis and only delegate to V1 if it is not found there.
 *       That logic will be added once the V2 service exists with its own index; for now,
 *       with V1 as the only real backend, delegating directly is equivalent and avoids
 *       building a dependency on Redis before anything on the other side uses it.</li>
 * </ul>
 *
 * <p>{@code /api/v2/**} intentionally has no route here: it is handled by {@link V2StubController}
 * with an explicit 501, since the V2 service does not exist yet (it starts in Task #4).
 */
@Configuration
public class GatewayRoutesConfig {

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder,
                                @Value("${app.v1-legacy-monolith.base-url}") String v1BaseUrl) {
        return builder.routes()
                .route("v1-management", r -> r.path("/api/v1/**")
                        .uri(v1BaseUrl))
                .route("v1-public-redirect", r -> r.path("/{shortCode}")
                        .and().method(HttpMethod.GET)
                        .uri(v1BaseUrl))
                .build();
    }
}
