package com.artmendez.urlshortener.v1.web;

import com.artmendez.urlshortener.v1.domain.UrlRecord;
import com.artmendez.urlshortener.v1.messaging.ClickEvent;
import com.artmendez.urlshortener.v1.messaging.ClickEventPublisher;
import com.artmendez.urlshortener.v1.service.ShortCodeNotFoundException;
import com.artmendez.urlshortener.v1.service.UrlExpiredException;
import com.artmendez.urlshortener.v1.service.UrlShortenerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

/**
 * Public API of Monolith V1: create short URLs and redirect.
 *
 * <p>No authentication (out of scope for V1; OAuth2/OIDC is added in the V2 redesign,
 * see ARCHITECTURE.md, section 5).
 *
 * <p>Brownfield scenario (Task #5): {@code expiresAt} is optional in the request (omitting it
 * or sending {@code null} preserves the original behavior: the URL never expires). Resolving
 * an expired short code returns HTTP 410 Gone.
 */
@RestController
public class UrlController {

    private final UrlShortenerService service;
    private final ClickEventPublisher clickEventPublisher;

    public UrlController(UrlShortenerService service, ClickEventPublisher clickEventPublisher) {
        this.service = service;
        this.clickEventPublisher = clickEventPublisher;
    }

    @PostMapping("/api/v1/urls")
    public ResponseEntity<CreateUrlResponse> create(@RequestBody CreateUrlRequest request) {
        UrlRecord record = service.createShortUrl(request.longUrl(), request.expiresAt());
        CreateUrlResponse body = new CreateUrlResponse(
                record.getShortCode(), record.getLongUrl(), record.getCreatedAt(), record.getExpiresAt());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(
            @PathVariable("shortCode") String shortCode, HttpServletRequest request) {
        UrlRecord record = service.resolve(shortCode);
        // The click event is only published for a successful redirect: if resolve() throws
        // (404 or 410), this line is never reached and an invalid click is not counted.
        clickEventPublisher.publish(new ClickEvent(
                record.getShortCode(),
                "v1",
                OffsetDateTime.now(),
                request.getRemoteAddr(),
                request.getHeader(HttpHeaders.USER_AGENT),
                request.getHeader(HttpHeaders.REFERER)));
        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                .header(HttpHeaders.LOCATION, record.getLongUrl())
                .build();
    }

    @ExceptionHandler(ShortCodeNotFoundException.class)
    public ResponseEntity<Void> handleNotFound() {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(UrlExpiredException.class)
    public ResponseEntity<Void> handleExpired() {
        return ResponseEntity.status(HttpStatus.GONE).build();
    }

    public record CreateUrlRequest(@NotBlank String longUrl, OffsetDateTime expiresAt) {

        /**
         * Original constructor, preserved unchanged so as not to break existing callers
         * (including the characterization tests, which instantiate this record with a single
         * argument). Equivalent to a request with no expiration date.
         */
        public CreateUrlRequest(String longUrl) {
            this(longUrl, null);
        }
    }

    public record CreateUrlResponse(String shortCode, String longUrl, OffsetDateTime createdAt, OffsetDateTime expiresAt) {
    }
}
