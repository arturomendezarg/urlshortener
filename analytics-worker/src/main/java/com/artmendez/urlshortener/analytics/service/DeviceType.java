package com.artmendez.urlshortener.analytics.service;

/**
 * Coarse device classification stored per click event (see ARCHITECTURE.md, section 4.2,
 * {@code click_events.device_type}).
 *
 * <p>This is an analytics classification, not the redirect-routing decision described in
 * ARCHITECTURE.md section 6, Scenario C, point 1 ("conditional redirect by device type via
 * {@code redirect_rules}") — that logic belongs to the V2 Redirect &amp; Cache Service (Task
 * #9) and decides which URL a user is sent to. This enum only records, after the fact, what
 * kind of device generated a click, for reporting purposes; it uses the same MOBILE/DESKTOP
 * vocabulary because the two features describe the same real-world distinction, not because
 * they share code today.
 */
public enum DeviceType {
    MOBILE,
    DESKTOP,
    UNKNOWN
}
