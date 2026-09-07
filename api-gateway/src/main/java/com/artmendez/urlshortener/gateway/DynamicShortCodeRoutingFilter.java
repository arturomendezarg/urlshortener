package com.artmendez.urlshortener.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;

/**
 * Decides, per request, whether {@code GET /{shortCode}} actually forwards to V1 or V2 -- the
 * real Strangler Fig dispatch ARCHITECTURE.md section 3.1 describes, and that
 * {@link GatewayRoutesConfig}'s own comment always said was pending "once V2 has its own index."
 *
 * <p>This used to answer that question by checking a Redis key V2 itself maintained
 * ({@code ShortLinkCache}, key prefix {@code shortlink:v2:}) as a side effect of creating or
 * reading a code. That made Redis a second, independent source of truth about what exists in
 * V2 -- and one that could disagree with V2's own database: a code created during the
 * "cutover commit" step (see {@code AI_USAGE_LOG.md}) is real in V2's Postgres the instant the
 * create call returns, but the Redis key for it is only written later, whenever something
 * first reads it through V2 directly. Any V2 code the Gateway is asked to route BEFORE that
 * first direct read was, from Redis's point of view, indistinguishable from a code that had
 * never existed at all -- so the Gateway sent it to V1, which correctly 404'd, because V1 had
 * genuinely never heard of it either. That was a real, reproducible defect (see this project's
 * Postman collection, folder "2. Brownfield", before this fix), not a hypothetical: the two
 * requests bracketing "Read the new V2 code directly" demonstrated a code going from
 * unreachable to reachable through the Gateway with no change to V2 itself at all, only to
 * whether Redis happened to have been warmed yet by a prior direct read.
 *
 * <p>Fixed here by removing the shared state instead of patching around it: this filter now
 * asks V2 directly whether a code exists (a {@code GET /{shortCode}} probe against V2 itself,
 * routing to V2 on anything other than a {@code 404} -- a {@code 410 Gone} for an expired V2
 * link still means the code exists in V2, and V2 is the one that must answer it, not V1),
 * instead of consulting an index that could fall behind V2's own database. V2's database is now
 * the only place this decision is made against, which is also the only place a V2 code can ever
 * become real in the first place -- there is no second copy of that fact left to drift out of
 * sync. The trade-off is an extra network round trip to V2 for every request the Gateway ends up
 * routing to V1 (a short code that was never in V2 now costs one failed probe before falling
 * back, instead of one fast Redis lookup); that cost is paid only by V1 traffic, and shrinks on
 * its own as more of V1's codes get "cutover commit"-ed into V2 over time, in the same spirit as
 * the Strangler Fig pattern this Gateway already implements.
 *
 * <p>Registered as this one route's own filter (see {@link GatewayRoutesConfig}, not a global
 * filter), at {@link #ORDER}: one past {@code RouteToRequestUrlFilter}'s fixed order of 10000, so
 * it always runs AFTER that filter has set its default (the route's static V1 URI) and BEFORE
 * {@code NettyRoutingFilter} (last of all, {@link Ordered#LOWEST_PRECEDENCE}) forwards the
 * request using whatever URI is left in the {@code GATEWAY_REQUEST_URL_ATTR} exchange attribute.
 * This is Spring Cloud Gateway's own documented mechanism for choosing a destination
 * dynamically (the same one {@code RouteToRequestUrlFilter} itself uses) -- nothing here reaches
 * into private API.
 *
 * <p>Honest caveat: this class could not be exercised with {@code mvn verify} in the sandbox that
 * wrote it (no route to Maven Central here -- see ARCHITECTURE.md section 8.2). It is written
 * against Spring WebClient's documented reactive contract and accompanied by
 * {@code GatewayRoutingIntegrationTest} and {@code DynamicShortCodeRoutingFilterV2OutageTest}
 * (fake V1/V2 HTTP backends, no external service required), but running those tests for real, in
 * the engineer's own Codespace, is what actually verifies it -- exactly the same review
 * discipline every other PR in this project already follows, not an exception made for this one.
 */
@Component
public class DynamicShortCodeRoutingFilter implements GatewayFilter, Ordered {

    private static final int ORDER = 10_001;
    private static final Duration V2_EXISTENCE_CHECK_TIMEOUT = Duration.ofSeconds(3);

    private final WebClient v2Client;
    private final URI v1BaseUri;
    private final URI v2BaseUri;

    public DynamicShortCodeRoutingFilter(
            WebClient.Builder webClientBuilder,
            @Value("${app.v1-legacy-monolith.base-url}") String v1BaseUrl,
            @Value("${app.v2-shortener-service.base-url}") String v2BaseUrl) {
        this.v1BaseUri = URI.create(v1BaseUrl);
        this.v2BaseUri = URI.create(v2BaseUrl);
        this.v2Client = webClientBuilder.baseUrl(v2BaseUrl).build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getRawPath();
        String shortCode = path.startsWith("/") ? path.substring(1) : path;

        return existsInV2(shortCode)
                .map(existsInV2 -> Boolean.TRUE.equals(existsInV2) ? v2BaseUri : v1BaseUri)
                .map(target -> UriComponentsBuilder.fromUri(target).path(path).build(true).toUri())
                .flatMap(target -> {
                    exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, target);
                    return chain.filter(exchange);
                });
    }

    /**
     * {@code true} unless V2 itself says {@code 404} (a {@code 410 Gone} for an expired link
     * still counts as "exists in V2" -- V2 must be the one to answer that, not V1).
     *
     * <p>Deliberately a {@code GET}, not a {@code HEAD}: V2's own {@code SecurityConfig} only
     * permits unauthenticated {@code GET /{shortCode}} ({@code requestMatchers(HttpMethod.GET,
     * "/{shortCode}")}), not HEAD -- an unauthenticated HEAD falls through to
     * {@code anyRequest().authenticated()} and gets a {@code 401}, which this method would then
     * (correctly, given its own contract) read as "exists in V2" and route a V1-only code to V2
     * by mistake. Real defect this class shipped with initially -- caught by running the
     * Brownfield Postman flow for real, not by the accompanying unit tests, which fake V2 as an
     * unauthenticated HTTP server and so never exercised V2's actual Spring Security rules; see
     * AI_USAGE_LOG.md. Using GET costs nothing extra over HEAD here: {@code redirect} returns
     * {@code ResponseEntity<Void>}, so neither verb ever has a response body to transfer.
     *
     * <p>Any failure to reach V2 at all -- connection refused, DNS failure, or this call
     * outrunning {@link #V2_EXISTENCE_CHECK_TIMEOUT} -- must never take the redirect down, so it
     * is treated exactly like "not found in V2": a real V2 code briefly falls back to V1's 404
     * during a genuine V2 outage (not a wrong answer -- V2 itself is unreachable either way), and
     * a V1-only code was never going to be found here regardless. Same "fail safe, not fail
     * closed" call the Redis-backed version of this filter used to make, now against the one
     * dependency (V2 itself) this decision actually rests on instead of two.
     */
    private Mono<Boolean> existsInV2(String shortCode) {
        return v2Client.method(HttpMethod.GET)
                .uri("/{shortCode}", shortCode)
                .exchangeToMono(response -> response.releaseBody()
                        .thenReturn(response.statusCode().value() != 404))
                .timeout(V2_EXISTENCE_CHECK_TIMEOUT)
                .onErrorReturn(Boolean.FALSE);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
