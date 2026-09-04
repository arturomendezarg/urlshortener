package com.artmendez.urlshortener.bulk.config;

import com.artmendez.urlshortener.v2.validation.ReservedSlugs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Wires the framework-free {@link ReservedSlugs} (v2-shortener-contract) into a Spring bean for
 * this module, binding it to its own {@code app.shortlink.reserved-slugs} configuration — the
 * bulk-processor counterpart of v2-shortener-service's {@code ShortLinkBeansConfig}.
 */
@Configuration
public class BulkBeansConfig {

    @Bean
    public ReservedSlugs reservedSlugs(@Value("${app.shortlink.reserved-slugs}") List<String> reservedSlugs) {
        return new ReservedSlugs(reservedSlugs);
    }
}
