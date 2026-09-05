package com.artmendez.urlshortener.v2.validation;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reserved {@code customAlias} values that can never be assigned to a user-created short link
 * (ARCHITECTURE.md, section 5).
 *
 * <p>Moved here from {@code v2-shortener-service} in Day 3, Task #10 (Bulk Processor), which
 * needs the same reserved-slug check for {@code customAlias} values submitted in a bulk job.
 * Deliberately framework-free (no {@code @Component}/{@code @Value}), unlike its previous home:
 * v2-shortener-contract has no Spring dependency at all (see its {@code pom.xml}), and adding
 * one just for this class would be a worse trade than each caller building it from its own
 * externalized configuration. Both v2-shortener-service and bulk-processor keep a thin
 * {@code @Bean} method that reads {@code app.shortlink.reserved-slugs} and constructs this class
 * with it — see {@code ShortLinkBeansConfig} (v2-shortener-service) and {@code BulkBeansConfig}
 * (bulk-processor).
 */
public final class ReservedSlugs {

    private final Set<String> reserved;

    public ReservedSlugs(Collection<String> reservedSlugs) {
        this.reserved = reservedSlugs.stream()
                .map(slug -> slug.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isReserved(String slug) {
        return slug != null && reserved.contains(slug.toLowerCase(Locale.ROOT));
    }
}
