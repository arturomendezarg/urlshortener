package com.artmendez.urlshortener.v2.shortlink.config;

import com.artmendez.urlshortener.v2.validation.ReservedSlugs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Wires the framework-free {@link ReservedSlugs} (v2-shortener-contract, moved there in Day 3
 * Task #10 so bulk-processor can reuse it too — see its Javadoc) into a Spring bean, binding it
 * to this service's own {@code app.shortlink.reserved-slugs} configuration. bulk-processor has
 * its own equivalent {@code @Bean} method reading the same property name from its own
 * configuration, rather than the two services sharing a Spring {@code @Configuration} class,
 * which would have pulled a Spring dependency back into v2-shortener-contract.
 */
@Configuration
public class ShortLinkBeansConfig {

    @Bean
    public ReservedSlugs reservedSlugs(@Value("${app.shortlink.reserved-slugs}") List<String> reservedSlugs) {
        return new ReservedSlugs(reservedSlugs);
    }
}
