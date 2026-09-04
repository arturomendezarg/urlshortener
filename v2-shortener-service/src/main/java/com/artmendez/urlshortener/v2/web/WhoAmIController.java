package com.artmendez.urlshortener.v2.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Temporary diagnostic endpoint for Task #8 (see ARCHITECTURE.md section 7, Day 2): its only
 * purpose is proving end to end that a request bearing a real access token issued by the
 * Keycloak realm in {@code infra/keycloak/realm-export.json} is accepted, and one without a
 * token (or with an invalid one) is rejected with 401 — see {@code SecurityConfig}.
 *
 * <p>Deliberately NOT part of the OpenAPI v2 contract ({@code v2-shortener-contract}): it is not
 * one of the six documented endpoints. This mirrors the precedent set by {@code
 * V2StubController} in {@code api-gateway} (Task #3): a clearly labeled, temporary component
 * that will be reconsidered — kept as a genuine health/debug endpoint, repurposed, or removed —
 * once Task #9 adds the real, contract-defined V2 endpoints.
 */
@RestController
@RequestMapping("/api/v2/_internal")
public class WhoAmIController {

    @GetMapping("/whoami")
    public Map<String, Object> whoAmI(@AuthenticationPrincipal Jwt jwt) {
        return Map.of(
                "subject", jwt.getSubject(),
                "username", jwt.getClaimAsString("preferred_username"),
                "issuer", jwt.getIssuer().toString());
    }
}
