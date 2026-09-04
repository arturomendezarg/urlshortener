package com.artmendez.urlshortener.v2.shortlink.domain;

/**
 * A single conditional-redirect rule, matching the {@code RedirectRule} schema in
 * {@code v2-shortener-contract}'s {@code shortener-v2.yaml} (see ARCHITECTURE.md, section 6,
 * Scenario C: "smart" links were deliberately scoped down to this simple by-device-type
 * redirect, not a generic rules engine).
 *
 * <p>A {@link ShortLink} carries these as a JSON array persisted in its {@code redirect_rules}
 * jsonb column and cached verbatim alongside the rest of the entry (see
 * {@code com.artmendez.urlshortener.v2.shortlink.cache.CachedShortLink}).
 */
public record RedirectRule(RedirectDeviceType deviceType, String targetUrl) {
}
