package com.artmendez.urlshortener.v1.web;

import com.artmendez.urlshortener.v1.domain.UrlRecord;
import com.artmendez.urlshortener.v1.service.ShortCodeNotFoundException;
import com.artmendez.urlshortener.v1.service.UrlShortenerService;
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
 * API publica del Monolito V1: crear URLs cortas y redirigir.
 *
 * <p>Sin autenticacion (fuera de alcance para V1; OAuth2/OIDC se agrega en el rediseno V2,
 * ver ARCHITECTURE.md, seccion 5).
 */
@RestController
public class UrlController {

    private final UrlShortenerService service;

    public UrlController(UrlShortenerService service) {
        this.service = service;
    }

    @PostMapping("/api/v1/urls")
    public ResponseEntity<CreateUrlResponse> create(@RequestBody CreateUrlRequest request) {
        UrlRecord record = service.createShortUrl(request.longUrl());
        CreateUrlResponse body = new CreateUrlResponse(
                record.getShortCode(), record.getLongUrl(), record.getCreatedAt());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode) {
        UrlRecord record = service.resolve(shortCode);
        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                .header(HttpHeaders.LOCATION, record.getLongUrl())
                .build();
    }

    @ExceptionHandler(ShortCodeNotFoundException.class)
    public ResponseEntity<Void> handleNotFound() {
        return ResponseEntity.notFound().build();
    }

    public record CreateUrlRequest(@NotBlank String longUrl) {
    }

    public record CreateUrlResponse(String shortCode, String longUrl, OffsetDateTime createdAt) {
    }
}
