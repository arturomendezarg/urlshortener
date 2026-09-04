package com.artmendez.urlshortener.v2.shortlink.domain;

/**
 * Device-type vocabulary for conditional redirects (see ARCHITECTURE.md, section 6, Scenario C,
 * point 1 and the {@code RedirectRule} schema in {@code shortener-v2.yaml}).
 *
 * <p>{@link #DEFAULT} only ever appears as the {@code deviceType} of a {@code RedirectRule}
 * (the catch-all rule to use when none of the more specific rules match the requesting
 * device); {@link com.artmendez.urlshortener.v2.shortlink.service.RedirectDeviceClassifier}
 * never returns it when classifying an incoming request.
 *
 * <p>Deliberately independent from {@code analytics-worker}'s own {@code DeviceType} enum
 * (MOBILE/DESKTOP/UNKNOWN): that one is a best-effort analytics classification recorded after
 * the fact for reporting; this one is a routing decision made before serving the redirect, with
 * different fallback semantics (DEFAULT rule, not "unknown"). Same real-world distinction,
 * intentionally not shared as a common module (see ARCHITECTURE.md's stance on not introducing
 * a shared module prematurely).
 */
public enum RedirectDeviceType {
    MOBILE,
    DESKTOP,
    DEFAULT
}
