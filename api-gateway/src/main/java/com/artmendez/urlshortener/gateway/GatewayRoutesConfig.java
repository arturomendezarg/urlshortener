package com.artmendez.urlshortener.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

/**
 * Gateway routes. Three active routes, applying the <b>Strangler Fig</b> pattern
 * (ARCHITECTURE.md section 3.1):
 * <ul>
 *   <li>{@code /api/v1/**} (control plane) -> Monolith V1, as-is.</li>
 *   <li>{@code /api/v2/**} (control plane) -> the real V2 microservice, as-is. This route used
 *       to be a documented {@code 501} stub ({@code V2StubController}, removed by this change)
 *       from when V2 did not exist yet; V2 has existed for a while now, so the stub was already
 *       stale dead code by the time this was fixed -- see {@code infra/k8s/README.md}'s own
 *       "pre-existing limitation, not introduced by this PR" note, which is exactly the gap this
 *       closes.</li>
 *   <li>{@code GET /{shortCode}} (data plane, the actual redirect) -> decided per request by
 *       {@link DynamicShortCodeRoutingFilter}, not by this route's own (placeholder) URI: it
 *       asks V2 directly whether the code exists there and forwards to V2 if so, V1 otherwise
 *       (see that filter's own Javadoc for why this asks V2 directly rather than a shared
 *       index). This is what makes Strangler Fig actually work for a shortener -- the public
 *       domain is a single, stable one, even as the backend behind any given code changes over
 *       time. The route's own {@code .uri(v1BaseUrl)} below is only
 *       {@code RouteToRequestUrlFilter}'s starting point; the filter always overrides it.</li>
 * </ul>
 */
@Configuration
public class GatewayRoutesConfig {

    @Bean
    public RouteLocator routes(
            RouteLocatorBuilder builder,
            @Value("${app.v1-legacy-monolith.base-url}") String v1BaseUrl,
            @Value("${app.v2-shortener-service.base-url}") String v2BaseUrl,
            DynamicShortCodeRoutingFilter dynamicShortCodeRoutingFilter) {
        return builder.routes()
                .route("v1-management", r -> r.path("/api/v1/**")
                        .uri(v1BaseUrl))
                .route("v2-management", r -> r.path("/api/v2/**")
                        .uri(v2BaseUrl))
                .route("public-redirect", r -> r.path("/{shortCode}")
                        .and().method(HttpMethod.GET)
                        .filters(f -> f.filter(dynamicShortCodeRoutingFilter))
                        .uri(v1BaseUrl))
                .build();
    }
}
