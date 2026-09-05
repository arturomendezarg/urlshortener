package com.artmendez.urlshortener.v2.shortlink.service;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

/**
 * Validates a {@code longUrl} before it is persisted, to prevent open-redirect / SSRF abuse
 * (ARCHITECTURE.md, section 5): only {@code http}/{@code https} are allowed schemes, and hosts
 * that resolve to an internal/private address are rejected, including the well-known cloud
 * metadata endpoint (169.254.169.254, covered by the link-local range check below).
 *
 * <p>Scheme is checked before host presence/resolution, deliberately: an opaque URI like
 * {@code javascript:alert(1)} or {@code mailto:someone@example.com} has no host at all, and the
 * more useful, specific rejection reason for those is "wrong scheme", not "missing host".
 *
 * <p>Resolution failures fail CLOSED (rejected), not open: a host that cannot be verified as
 * safe is treated as unsafe, rather than assuming the best. This is a deliberate, declared
 * trade-off — it means a legitimately unreachable-at-validation-time host is also rejected, with
 * no distinction made between "doesn't exist" and "we couldn't check right now".
 */
public final class LongUrlValidator {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
    private static final Set<String> BLOCKED_HOSTNAMES = Set.of("localhost");

    private LongUrlValidator() {
    }

    public static void validate(String longUrl) {
        if (longUrl == null || longUrl.isBlank()) {
            throw new InvalidLongUrlException("longUrl must not be blank");
        }
        URI uri;
        try {
            uri = new URI(longUrl);
        } catch (URISyntaxException e) {
            throw new InvalidLongUrlException("longUrl is not a valid URL: '" + longUrl + "'");
        }
        validateScheme(uri);
        validateHost(uri, longUrl);
    }

    private static void validateScheme(URI uri) {
        String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT);
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme)) {
            throw new InvalidLongUrlException(
                    "longUrl scheme must be http or https, got: '" + uri.getScheme() + "'");
        }
    }

    private static void validateHost(URI uri, String longUrl) {
        String host = uri.getHost();
        if (host == null) {
            throw new InvalidLongUrlException("longUrl must be an absolute URL with a host: '" + longUrl + "'");
        }
        if (BLOCKED_HOSTNAMES.contains(host.toLowerCase(Locale.ROOT))) {
            throw new InvalidLongUrlException("longUrl host is not allowed (internal host): '" + host + "'");
        }
        // URI.getHost() keeps the enclosing brackets for an IPv6 literal (e.g. "[::1]"), but
        // InetAddress.getByName expects the bare address without them.
        String resolvableHost = host.startsWith("[") && host.endsWith("]")
                ? host.substring(1, host.length() - 1)
                : host;
        InetAddress address;
        try {
            address = InetAddress.getByName(resolvableHost);
        } catch (UnknownHostException e) {
            throw new InvalidLongUrlException("longUrl host could not be resolved: '" + host + "'");
        }
        if (address.isLoopbackAddress()
                || address.isSiteLocalAddress()
                || address.isLinkLocalAddress()
                || address.isAnyLocalAddress()
                || address.isMulticastAddress()) {
            throw new InvalidLongUrlException(
                    "longUrl host resolves to a private/internal address, rejected to prevent SSRF: '"
                            + host + "'");
        }
    }
}
