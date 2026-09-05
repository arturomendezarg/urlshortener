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
 *
 * <p>The compact constructor copies {@code redirectRules} into an unmodifiable list: without it,
 * SpotBugs correctly flags both the canonical constructor (storing the caller's own mutable
 * list, {@code EI_EXPOSE_REP2}) and the canonical accessor (handing that same live reference
 * back out, {@code EI_EXPOSE_REP}) -- Jackson deserializes into a genuinely mutable
 * {@code ArrayList} here, so this is a real, reachable case, not just a theoretical one.
 */
public record CachedShortLink(
        String longUrl,
        List<RedirectRule> redirectRules,
        OffsetDateTime expiresAt,
        boolean active) {

    public CachedShortLink {
        redirectRules = redirectRules == null ? List.of() : List.copyOf(redirectRules);
    }
}
