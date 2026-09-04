package com.artmendez.urlshortener.v2.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
 *   <li>Only the actuator health/info/prometheus endpoints are public; everything else requires
 *       a valid JWT. There is no fine-grained, per-endpoint rule yet (e.g. the public {@code GET
 *       /{shortCode}} redirect from ARCHITECTURE.md section 5) because those business endpoints
 *       do not exist in this module yet — they are Task #9's scope, built on top of this
 *       config, and will need their own explicit {@code permitAll()} rule at that point.
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
                        .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }
}
