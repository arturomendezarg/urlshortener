package com.artmendez.urlshortener.v2.shortlink.web;

import com.artmendez.urlshortener.v2.shortlink.domain.ShortLink;
import com.artmendez.urlshortener.v2.shortlink.service.DuplicateAliasException;
import com.artmendez.urlshortener.v2.shortlink.service.InvalidLongUrlException;
import com.artmendez.urlshortener.v2.shortlink.service.ReservedSlugException;
import com.artmendez.urlshortener.v2.shortlink.service.ShortLinkExpiredException;
import com.artmendez.urlshortener.v2.shortlink.service.ShortLinkNotFoundException;
import com.artmendez.urlshortener.v2.shortlink.service.ShortLinkService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

/**
 * The two runtime V2 endpoints added in Task #9: creation (under the versioned, authenticated
 * {@code /api/v2} contract) and the public redirect. Both live in this one module for this
 * exercise's scope, even though ARCHITECTURE.md section 3.1 describes "Shortener Service"
 * (creation) and "Redirect & Cache Service" (redirect) as distinct logical microservices — no
 * separate task/module is scheduled for creation, and the redirect service needs creatable data
 * to resolve against.
 *
 * <p>{@code GET /{shortCode}} is deliberately NOT under {@code /api/v2}: the OpenAPI contract's
 * own "out of scope" note explains the public short-link domain is single and stable, normally
 * resolved by the Gateway's V2-vs-V1 Strangler Fig routing. Serving it directly here (rather
 * than only documenting it) is this exercise's pragmatic scope simplification, declared rather
 * than hidden.
 */
@RestController
public class ShortLinkController {

    private final ShortLinkService service;
    private final String baseUrl;

    public ShortLinkController(
            ShortLinkService service, @Value("${app.shortlink.base-url}") String baseUrl) {
        this.service = service;
        this.baseUrl = baseUrl;
    }

    @PostMapping("/api/v2/urls")
    public ResponseEntity<CreateUrlResponse> create(
            @Valid @RequestBody CreateUrlRequest request, @AuthenticationPrincipal Jwt jwt) {
        ShortLink shortLink = service.create(
                request.longUrl(),
                request.customAlias(),
                request.expiresAt(),
                request.redirectRules(),
                jwt.getSubject());
        CreateUrlResponse body = new CreateUrlResponse(
                shortLink.getShortCode(),
                baseUrl + "/" + shortLink.getShortCode(),
                shortLink.getOwnerUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(
            @PathVariable("shortCode") String shortCode, HttpServletRequest request) {
        String targetUrl = service.resolve(shortCode, request.getHeader(HttpHeaders.USER_AGENT));
        // 302 Found, not 301: the target can legitimately differ between requests to the SAME
        // short code (conditional redirect by device type, ARCHITECTURE.md section 6, Scenario
        // C) and must stop resolving once the link expires. A 301 is meant to be cached
        // permanently by the browser/CDN, which would freeze in the first device's target (or a
        // pre-expiry target) forever — the opposite of what this feature needs.
        // v1-legacy-monolith's simpler redirect (no per-device targets) correctly uses 301; this
        // is a deliberate departure, not an inconsistency between the two services.
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, targetUrl)
                .build();
    }

    @ExceptionHandler(ShortLinkNotFoundException.class)
    public ResponseEntity<Void> handleNotFound() {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(ShortLinkExpiredException.class)
    public ResponseEntity<Void> handleExpired() {
        return ResponseEntity.status(HttpStatus.GONE).build();
    }

    @ExceptionHandler(InvalidLongUrlException.class)
    public ResponseEntity<ErrorResponse> handleInvalidLongUrl(
            InvalidLongUrlException e, HttpServletRequest request) {
        return badRequest(e.getMessage(), request);
    }

    @ExceptionHandler(ReservedSlugException.class)
    public ResponseEntity<ErrorResponse> handleReservedSlug(
            ReservedSlugException e, HttpServletRequest request) {
        return badRequest(e.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException e, HttpServletRequest request) {
        return badRequest(e.getMessage(), request);
    }

    @ExceptionHandler(DuplicateAliasException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateAlias(
            DuplicateAliasException e, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                OffsetDateTime.now(),
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                e.getMessage(),
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    private ResponseEntity<ErrorResponse> badRequest(String message, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                OffsetDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                message,
                request.getRequestURI());
        return ResponseEntity.badRequest().body(body);
    }
}
