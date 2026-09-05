package com.artmendez.urlshortener.v2.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LongUrlValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {"https://example.com/path", "http://example.com", "https://example.com:8443/x?y=1"})
    void acceptsHttpAndHttpsUrlsWithAPublicHost(String longUrl) {
        assertThatCode(() -> LongUrlValidator.validate(longUrl)).doesNotThrowAnyException();
    }

    @Test
    void rejectsBlankUrl() {
        assertThatThrownBy(() -> LongUrlValidator.validate("  "))
                .isInstanceOf(InvalidLongUrlException.class);
    }

    @Test
    void rejectsAUrlWithNoHost() {
        assertThatThrownBy(() -> LongUrlValidator.validate("mailto:someone@example.com"))
                .isInstanceOf(InvalidLongUrlException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ftp://example.com/file", "javascript:alert(1)", "file:///etc/passwd"})
    void rejectsDisallowedSchemes(String longUrl) {
        assertThatThrownBy(() -> LongUrlValidator.validate(longUrl))
                .isInstanceOf(InvalidLongUrlException.class)
                .hasMessageContaining("scheme");
    }

    @Test
    void rejectsLocalhostByName() {
        assertThatThrownBy(() -> LongUrlValidator.validate("http://localhost:8080/admin"))
                .isInstanceOf(InvalidLongUrlException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1/",
            "http://[::1]/",
            "http://192.168.1.10/internal",
            "http://10.0.0.5/internal",
            "http://169.254.169.254/latest/meta-data" // cloud metadata endpoint (SSRF classic)
    })
    void rejectsUrlsResolvingToPrivateOrInternalAddresses(String longUrl) {
        assertThatThrownBy(() -> LongUrlValidator.validate(longUrl))
                .isInstanceOf(InvalidLongUrlException.class)
                .hasMessageContaining("SSRF");
    }
}
