package com.artmendez.urlshortener.v2.shortlink.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reserved {@code customAlias} values that can never be assigned to a user-created short link
 * (ARCHITECTURE.md, section 5). Configured via {@code app.shortlink.reserved-slugs} rather than
 * hardcoded, so the list can be extended without a code change.
 */
@Component
public class ReservedSlugs {

    private final Set<String> reserved;

    public ReservedSlugs(@Value("${app.shortlink.reserved-slugs}") List<String> reservedSlugs) {
        this.reserved = reservedSlugs.stream()
                .map(slug -> slug.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isReserved(String slug) {
        return slug != null && reserved.contains(slug.toLowerCase(Locale.ROOT));
    }
}
