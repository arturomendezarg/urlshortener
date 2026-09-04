package com.artmendez.urlshortener.analytics.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Anonymizes client IP addresses before they are persisted into {@code click_events} (see
 * ARCHITECTURE.md, section 6, Scenario C: "Respetar privacidad" -&gt; anonymize the last octet
 * of the IP before persisting the click event).
 *
 * <p>IPv4: the last octet is zeroed ({@code 192.168.1.45} -&gt; {@code 192.168.1.0}), matching
 * the literal acceptance criterion in ARCHITECTURE.md.
 *
 * <p>IPv6: the last 80 bits (the trailing 5 of 8 groups, i.e. the interface identifier) are
 * zeroed, keeping only the /48 network prefix — the same degree of anonymization in spirit
 * (drop the part that identifies a single device/user), applied to the address family the
 * IPv4-only acceptance criterion did not anticipate.
 *
 * <p>A value that is missing or cannot be parsed as an IP address at all is treated as missing
 * data, not a fatal error: this runs off the critical redirect path (see
 * {@link ClickEventProcessingService}), so it degrades to {@code null} and logs a warning
 * rather than failing message processing. {@link InetAddress#getByName(String)} does not
 * perform a DNS lookup when given a literal IP address — only its format is validated — so this
 * never makes an outbound network call.
 */
public final class IpAnonymizer {

    private static final Logger log = LoggerFactory.getLogger(IpAnonymizer.class);
    private static final int IPV6_INTERFACE_IDENTIFIER_START_BYTE = 6;

    private IpAnonymizer() {
    }

    public static String anonymize(String rawIp) {
        if (rawIp == null || rawIp.isBlank()) {
            return null;
        }
        try {
            InetAddress address = InetAddress.getByName(rawIp);
            byte[] bytes = address.getAddress();
            if (address instanceof Inet4Address) {
                bytes[bytes.length - 1] = 0;
            } else if (address instanceof Inet6Address) {
                for (int i = IPV6_INTERFACE_IDENTIFIER_START_BYTE; i < bytes.length; i++) {
                    bytes[i] = 0;
                }
            }
            return InetAddress.getByAddress(bytes).getHostAddress();
        } catch (UnknownHostException ex) {
            log.warn("Could not parse client IP '{}' for anonymization; storing null instead: {}", rawIp, ex.getMessage());
            return null;
        }
    }
}
