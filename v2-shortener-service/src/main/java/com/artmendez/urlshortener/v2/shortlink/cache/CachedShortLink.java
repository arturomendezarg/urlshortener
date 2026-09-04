package com.artmendez.urlshortener.v2.shortlink.cache;

import com.artmendez.urlshortener.v2.shortlink.domain.RedirectRule;
import com.artmendez.urlshortener.v2.shortlink.domain.ShortLink;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Cache-friendly snapshot of a {@link ShortLink}, serialized as JSON into Redis by
 * {@link ShortLinkCache}. Deliberately a separate type from the JPA entity: it carries the
 * decoded {@link RedirectRule} list (not the raw {@code redirect_rules} JSON string) and nothing
 * persistence-specific (no {@code id}, no Hibernate proxying concerns), so it serializes cleanly
 * with a plain Jackson {@code ObjectMapper}.
 */
public record CachedShortLink(
        String longUrl,
        List<RedirectRule> redirectRules,
        OffsetDateTime expiresAt,
        boolean active) {
}
