package com.artmendez.urlshortener.v2.shortlink.service;

import com.artmendez.urlshortener.v2.shortlink.domain.RedirectDeviceType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RedirectDeviceClassifierTest {

    @Test
    void classifiesKnownMobileUserAgentsAsMobile() {
        assertThat(RedirectDeviceClassifier.classify(
                        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15"))
                .isEqualTo(RedirectDeviceType.MOBILE);
        assertThat(RedirectDeviceClassifier.classify(
                        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 Mobile Safari/537.36"))
                .isEqualTo(RedirectDeviceType.MOBILE);
    }

    @Test
    void classifiesDesktopUserAgentAsDesktop() {
        assertThat(RedirectDeviceClassifier.classify(
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0"))
                .isEqualTo(RedirectDeviceType.DESKTOP);
    }

    @Test
    void classifiesMissingUserAgentAsDesktopNotAThirdBucket() {
        assertThat(RedirectDeviceClassifier.classify(null)).isEqualTo(RedirectDeviceType.DESKTOP);
        assertThat(RedirectDeviceClassifier.classify("   ")).isEqualTo(RedirectDeviceType.DESKTOP);
    }
}
