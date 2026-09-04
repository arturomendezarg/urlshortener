package com.artmendez.urlshortener.analytics.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IpAnonymizerTest {

    @Test
    void zeroesLastOctetOfIpv4Address() {
        assertThat(IpAnonymizer.anonymize("192.168.1.45")).isEqualTo("192.168.1.0");
    }

    @Test
    void isIdempotentOnAlreadyAnonymizedIpv4Address() {
        assertThat(IpAnonymizer.anonymize("10.0.0.0")).isEqualTo("10.0.0.0");
    }

    @Test
    void zeroesInterfaceIdentifierOfIpv6Address() {
        assertThat(IpAnonymizer.anonymize("2001:db8:85a3:1234:5678:8a2e:370:7334"))
                .isEqualTo("2001:db8:85a3::");
    }

    @Test
    void returnsNullForBlankInput() {
        assertThat(IpAnonymizer.anonymize("")).isNull();
        assertThat(IpAnonymizer.anonymize(null)).isNull();
    }

    @Test
    void returnsNullForUnparsableInput() {
        assertThat(IpAnonymizer.anonymize("not-an-ip")).isNull();
    }
}
