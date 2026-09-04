package com.artmendez.urlshortener.v2.shortlink.web;

import com.artmendez.urlshortener.v2.shortlink.domain.RedirectRule;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Request body for {@code POST /api/v2/urls}, matching {@code CreateUrlRequest} in
 * {@code v2-shortener-contract}'s {@code shortener-v2.yaml} field for field.
 *
 * <p>The compact constructor copies {@code redirectRules} into an unmodifiable list for the
 * same reason as {@code CachedShortLink}: Jackson binds the JSON request body's array into a
 * genuinely mutable {@code ArrayList}, so without this, SpotBugs correctly flags both the
 * canonical constructor ({@code EI_EXPOSE_REP2}) and the canonical accessor
 * ({@code EI_EXPOSE_REP}). {@code null} (no {@code redirectRules} in the request) is left as
 * {@code null}, not normalized to an empty list: {@code ShortLinkService.create} already treats
 * "null" and "empty" identically, so this changes nothing downstream.
 */
public record CreateUrlRequest(
        @NotBlank String longUrl,
        @Pattern(regexp = "^[0-9A-Za-z_-]{3,20}$") String customAlias,
        OffsetDateTime expiresAt,
        List<RedirectRule> redirectRules) {

    public CreateUrlRequest {
        redirectRules = redirectRules == null ? null : List.copyOf(redirectRules);
    }
}
