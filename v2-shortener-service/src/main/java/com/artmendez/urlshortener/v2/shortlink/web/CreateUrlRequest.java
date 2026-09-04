package com.artmendez.urlshortener.v2.shortlink.web;

import com.artmendez.urlshortener.v2.shortlink.domain.RedirectRule;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Request body for {@code POST /api/v2/urls}, matching {@code CreateUrlRequest} in
 * {@code v2-shortener-contract}'s {@code shortener-v2.yaml} field for field.
 */
public record CreateUrlRequest(
        @NotBlank String longUrl,
        @Pattern(regexp = "^[0-9A-Za-z_-]{3,20}$") String customAlias,
        OffsetDateTime expiresAt,
        List<RedirectRule> redirectRules) {
}
