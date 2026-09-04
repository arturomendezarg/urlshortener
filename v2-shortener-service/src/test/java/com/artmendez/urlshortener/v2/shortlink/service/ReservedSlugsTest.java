package com.artmendez.urlshortener.v2.shortlink.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReservedSlugsTest {

    private final ReservedSlugs reservedSlugs = new ReservedSlugs(List.of("api", "admin", "health", "actuator"));

    @Test
    void flagsConfiguredSlugsAsReservedCaseInsensitively() {
        assertThat(reservedSlugs.isReserved("api")).isTrue();
        assertThat(reservedSlugs.isReserved("ADMIN")).isTrue();
        assertThat(reservedSlugs.isReserved("Health")).isTrue();
    }

    @Test
    void doesNotFlagAnOrdinaryAliasAsReserved() {
        assertThat(reservedSlugs.isReserved("my-cool-link")).isFalse();
    }

    @Test
    void treatsNullAsNotReserved() {
        assertThat(reservedSlugs.isReserved(null)).isFalse();
    }
}
