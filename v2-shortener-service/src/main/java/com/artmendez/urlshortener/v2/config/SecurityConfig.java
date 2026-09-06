package com.artmendez.urlshortener.v2.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * OAuth2 Resource Server configuration (Day 2, Task #8; see ARCHITECTURE.md section 3 "V2
 * services act as an OAuth2 Resource Server" and section 5 "AuthN/AuthZ: OIDC via Keycloak").
 *
 * <p>This service issues no tokens and has no login page of its own — it only validates Bearer
 * JWTs against the realm defined in {@code infra/keycloak/realm-export.json}, via {@code
 * spring.security.oauth2.resourceserver.jwt.issuer-uri} (see {@code application.yml}), which
 * makes Spring Security fetch Keycloak's OIDC discovery document and JWK set automatically.
 *
 * <p><b>Explicit design decisions:</b>
 * <ul>
 *   <li>CSRF protection is disabled and sessions are stateless: this is a pure Bearer-token REST
 *       API with no cookie-based session or browser form login, so CSRF (which protects
 *       cookie-authenticated state-changing requests) does not apply here.
 *   <li>The actuator health/info/prometheus endpoints and {@code GET /{shortCode}} (the public
 *       redirect, Task #9) are the only public routes; everything else — including
 *       {@code POST /api/v2/urls} — requires a valid JWT. {@code GET /{shortCode}} must stay
 *       public: it is the link a browser follows with no Authorization header at all, per
 *       ARCHITECTURE.md section 5 and the OpenAPI contract's own note that this path is
 *       deliberately outside the authenticated {@code /api/v2} contract.
 *   <li>{@code /actuator/health/**} is permitted separately from {@code /actuator/health}, and
 *       that is not redundancy: an exact matcher does NOT cover the
 *       {@code /actuator/health/readiness} and {@code /actuator/health/liveness} sub-paths that
 *       Spring Boot creates on its own once it detects it is running on Kubernetes. Those are
 *       precisely the paths a kubelet probes, with no Authorization header, so with only the
 *       exact matcher every probe got a 401 — the pod never turned Ready, and Kubernetes
 *       eventually terminated a perfectly healthy container when its startup probe budget ran
 *       out. This service is the only OAuth2 Resource Server in the monorepo, which is why it
 *       was the only one affected. Found by running the stack on a real cluster, not by any
 *       test: {@code KeycloakResourceServerIntegrationTest} already asserted that
 *       {@code /actuator/health} was public, and passed the whole time. It now asserts the two
 *       probe paths as well. See AI_USAGE_LOG.md.
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**",
                                "/actuator/info", "/actuator/prometheus")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/{shortCode}")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }
}
