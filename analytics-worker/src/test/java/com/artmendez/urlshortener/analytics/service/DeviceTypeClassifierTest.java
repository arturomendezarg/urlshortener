package com.artmendez.urlshortener.analytics.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceTypeClassifierTest {

    @Test
    void classifiesKnownMobileUserAgentsAsMobile() {
        assertThat(DeviceTypeClassifier.classify(
                        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15"))
                .isEqualTo(DeviceType.MOBILE);
        assertThat(DeviceTypeClassifier.classify(
                        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 Mobile Safari/537.36"))
                .isEqualTo(DeviceType.MOBILE);
    }

    @Test
    void classifiesDesktopUserAgentAsDesktop() {
        assertThat(DeviceTypeClassifier.classify(
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0"))
                .isEqualTo(DeviceType.DESKTOP);
    }

    @Test
    void classifiesMissingUserAgentAsUnknown() {
        assertThat(DeviceTypeClassifier.classify(null)).isEqualTo(DeviceType.UNKNOWN);
        assertThat(DeviceTypeClassifier.classify("   ")).isEqualTo(DeviceType.UNKNOWN);
    }
}
