package com.artmendez.urlshortener.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;

/**
 * Decides, per request, whether {@code GET /{shortCode}} actually forwards to V1 or V2 -- the
 * real Strangler Fig dispatch ARCHITECTURE.md section 3.1 describes, and that
 * {@link GatewayRoutesConfig}'s own comment always said was pending "once V2 has its own index."
 * V2 has had one since it started writing to Redis ({@code ShortLinkCache}, key prefix
 * {@code shortlink:v2:}), but nobody wired this decision back into the Gateway -- confirmed still
 * true by rereading {@code GatewayRoutesConfig} and {@code infra/k8s/README.md}'s own
 * "pre-existing limitation, not introduced by this PR" note before writing this class. Without
 * it, the "V2 cutover alongside V1" brownfield scenario has nothing real to test: V2's own
 * controller already serves {@code GET /{shortCode}} fine on its own port, but the actual claim
 * this whole architecture makes -- one stable public domain, routed to whichever backend owns a
 * given code -- was never true at the one place a caller actually uses (the Gateway).
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
 * against Spring Cloud Gateway's documented {@code GATEWAY_REQUEST_URL_ATTR} contract and
 * accompanied by {@code DynamicShortCodeRoutingFilterTest} (real Redis via Testcontainers, fake
 * V1/V2 HTTP backends), but running that test for real, in the engineer's own Codespace, is what
 * actually verifies it -- exactly the same review discipline every other PR in this project
 * already follows, not an exception made for this one.
 */
@Component
public class DynamicShortCodeRoutingFilter implements GatewayFilter, Ordered {

    private static final String REDIS_KEY_PREFIX = "shortlink:v2:";
    private static final int ORDER = 10_001;

    private final ReactiveStringRedisTemplate redisTemplate;
    private final URI v1BaseUri;
    private final URI v2BaseUri;

    public DynamicShortCodeRoutingFilter(
            ReactiveStringRedisTemplate redisTemplate,
            @Value("${app.v1-legacy-monolith.base-url}") String v1BaseUrl,
            @Value("${app.v2-shortener-service.base-url}") String v2BaseUrl) {
        this.redisTemplate = redisTemplate;
        this.v1BaseUri = URI.create(v1BaseUrl);
        this.v2BaseUri = URI.create(v2BaseUrl);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getRawPath();
        String shortCode = path.startsWith("/") ? path.substring(1) : path;

        return redisTemplate.hasKey(REDIS_KEY_PREFIX + shortCode)
                // A Redis outage here must never take the redirect down -- same "fail safe, not
                // fail closed" call as ShortLinkCache's own Circuit Breaker fallback. Treating an
                // error exactly like "not found in V2" is safe in both directions: every code V2
                // ever created is still in the SAME Redis this call just failed to reach (so a
                // real V2 code briefly falls back to V1's 404 during an outage, not a wrong
                // answer), and every V1-only code was never going to be in that index anyway.
                .onErrorReturn(Boolean.FALSE)
                .defaultIfEmpty(Boolean.FALSE)
                .map(existsInV2 -> Boolean.TRUE.equals(existsInV2) ? v2BaseUri : v1BaseUri)
                .map(target -> UriComponentsBuilder.fromUri(target).path(path).build(true).toUri())
                .flatMap(target -> {
                    exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, target);
                    return chain.filter(exchange);
                });
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
