package com.artmendez.urlshortener.v2.shortlink.service;

import com.artmendez.urlshortener.v2.shortlink.domain.RedirectDeviceType;

import java.util.regex.Pattern;

/**
 * Infers a {@link RedirectDeviceType} from a raw {@code User-Agent} header, to decide which
 * {@code RedirectRule} (if any) applies to the current request (ARCHITECTURE.md, section 6,
 * Scenario C: "smart" was disambiguated as this simple by-device-type redirect).
 *
 * <p>A missing/blank User-Agent classifies as {@link RedirectDeviceType#DESKTOP} rather than a
 * third "unknown" bucket: unlike {@code analytics-worker}'s classifier (which records UNKNOWN
 * for honest reporting), this one must always produce a routing decision, and a request with no
 * User-Agent header looks more like a script or tool than a mobile browser.
 *
 * <p>Same lightweight regex-heuristic approach as {@code analytics-worker}'s
 * {@code DeviceTypeClassifier}, deliberately duplicated rather than shared: see that class's
 * Javadoc and this project's stance on not introducing a shared module prematurely.
 */
public final class RedirectDeviceClassifier {

    private static final Pattern MOBILE_TOKENS =
            Pattern.compile("Mobi|Android|iPhone|iPad|iPod|Windows Phone|BlackBerry", Pattern.CASE_INSENSITIVE);

    private RedirectDeviceClassifier() {
    }

    public static RedirectDeviceType classify(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return RedirectDeviceType.DESKTOP;
        }
        return MOBILE_TOKENS.matcher(userAgent).find() ? RedirectDeviceType.MOBILE : RedirectDeviceType.DESKTOP;
    }
}
