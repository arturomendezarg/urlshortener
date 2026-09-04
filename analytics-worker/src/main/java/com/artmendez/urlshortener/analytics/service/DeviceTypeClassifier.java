package com.artmendez.urlshortener.analytics.service;

import java.util.regex.Pattern;

/**
 * Infers a coarse {@link DeviceType} from a raw {@code User-Agent} header.
 *
 * <p>V1's click events do not carry a device type: see {@code v1-legacy-monolith}'s {@code
 * ClickEvent} Javadoc, which explains that V1 has no device-detection logic and deliberately
 * leaves the decision on how to fill this in to the Analytics Worker. This is a lightweight
 * heuristic, not a full user-agent parsing library — it looks for well-known mobile/tablet
 * tokens and falls back to DESKTOP. That is good enough for the analytics use case (aggregate
 * reporting), not for a security- or routing-sensitive decision.
 */
public final class DeviceTypeClassifier {

    private static final Pattern MOBILE_TOKENS =
            Pattern.compile("Mobi|Android|iPhone|iPad|iPod|Windows Phone|BlackBerry", Pattern.CASE_INSENSITIVE);

    private DeviceTypeClassifier() {
    }

    public static DeviceType classify(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return DeviceType.UNKNOWN;
        }
        return MOBILE_TOKENS.matcher(userAgent).find() ? DeviceType.MOBILE : DeviceType.DESKTOP;
    }
}
