package com.artmendez.urlshortener.analytics.service;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;

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

    /**
     * Compares parsed {@link InetAddress} objects rather than raw strings: Java's {@code
     * Inet6Address.getHostAddress()} prints every group in hex (dropping leading zeros within a
     * group) but does NOT apply RFC 5952's "::" compression for a run of all-zero groups — it
     * returned {@code "2001:db8:85a3:0:0:0:0:0"}, not {@code "2001:db8:85a3::"}, for this input
     * on the JDK used in CI. Comparing through {@code InetAddress.getByName(...)} (which accepts
     * both forms) makes the assertion correct regardless of which textual form the running JDK
     * happens to produce.
     */
    @Test
    void zeroesInterfaceIdentifierOfIpv6Address() throws Exception {
        String anonymized = IpAnonymizer.anonymize("2001:db8:85a3:1234:5678:8a2e:370:7334");
        assertThat(InetAddress.getByName(anonymized)).isEqualTo(InetAddress.getByName("2001:db8:85a3::"));
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
