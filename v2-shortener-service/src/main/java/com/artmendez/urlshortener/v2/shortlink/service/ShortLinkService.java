package com.artmendez.urlshortener.v2.shortlink.service;

import com.artmendez.urlshortener.v2.codec.Base62CodeGenerator;
import com.artmendez.urlshortener.v2.shortlink.cache.CachedShortLink;
import com.artmendez.urlshortener.v2.shortlink.cache.ShortLinkCache;
import com.artmendez.urlshortener.v2.shortlink.domain.RedirectDeviceType;
import com.artmendez.urlshortener.v2.shortlink.domain.RedirectRule;
import com.artmendez.urlshortener.v2.shortlink.domain.ShortLink;
import com.artmendez.urlshortener.v2.shortlink.repository.ShortLinkRepository;
import com.artmendez.urlshortener.v2.validation.LongUrlValidator;
import com.artmendez.urlshortener.v2.validation.ReservedSlugs;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Business logic for the two runtime endpoints added in Task #9 (ARCHITECTURE.md, section 6,
 * Scenario A and Scenario C): creating a short link and resolving one for redirect.
 */
@Service
public class ShortLinkService {

    private final ShortLinkRepository repository;
    private final ShortLinkCache cache;
    private final ReservedSlugs reservedSlugs;
    private final ObjectMapper objectMapper;
    private final Base62CodeGenerator codeGenerator = new Base62CodeGenerator();

    public ShortLinkService(
            ShortLinkRepository repository,
            ShortLinkCache cache,
            ReservedSlugs reservedSlugs,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.cache = cache;
        this.reservedSlugs = reservedSlugs;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates a new short link.
     *
     * @throws InvalidLongUrlException  if {@code longUrl} fails scheme/SSRF validation
     * @throws ReservedSlugException    if {@code customAlias} is a reserved slug
     * @throws DuplicateAliasException  if {@code customAlias} is already taken
     */
    @Transactional
    public ShortLink create(
            String longUrl,
            String customAlias,
            OffsetDateTime expiresAt,
            List<RedirectRule> redirectRules,
            String ownerUserId) {
        LongUrlValidator.validate(longUrl);

        String shortCode = resolveShortCode(customAlias);
        String redirectRulesJson = writeRedirectRules(redirectRules);
        ShortLink shortLink = new ShortLink(
                shortCode, longUrl, ownerUserId, redirectRulesJson, OffsetDateTime.now(), expiresAt);
        return repository.save(shortLink);
    }

    private String resolveShortCode(String customAlias) {
        if (customAlias == null || customAlias.isBlank()) {
            // Base62CodeGenerator (v2-shortener-contract, Day 1 Task #4) already handles
            // collision retry + fallback to a longer code; existsByShortCode is the only
            // repository-specific piece it needs from us.
            return codeGenerator.generate(repository::existsByShortCode);
        }
        if (reservedSlugs.isReserved(customAlias)) {
            throw new ReservedSlugException(customAlias);
        }
        if (repository.existsByShortCode(customAlias)) {
            throw new DuplicateAliasException(customAlias);
        }
        return customAlias;
    }

    /**
     * Resolves a short code to the URL it should redirect to for the requesting device, via the
     * Redis cache-aside pattern (falling back to PostgreSQL on a miss or a Redis outage — see
     * {@link ShortLinkCache}) and evaluating {@code redirectRules} by device type.
     *
     * @throws ShortLinkNotFoundException if no short link exists for {@code shortCode}
     * @throws ShortLinkExpiredException  if the short link is expired or deactivated
     */
    public String resolve(String shortCode, String userAgent) {
        CachedShortLink resolved = cache.get(shortCode).orElseGet(() -> loadAndCache(shortCode));

        if (!resolved.active() || isExpired(resolved.expiresAt())) {
            throw new ShortLinkExpiredException(shortCode);
        }

        RedirectDeviceType deviceType = RedirectDeviceClassifier.classify(userAgent);
        return pickTargetUrl(resolved, deviceType);
    }

    private CachedShortLink loadAndCache(String shortCode) {
        ShortLink shortLink = repository.findByShortCode(shortCode)
                .orElseThrow(() -> new ShortLinkNotFoundException(shortCode));
        CachedShortLink cached = new CachedShortLink(
                shortLink.getLongUrl(),
                readRedirectRules(shortLink.getRedirectRulesJson()),
                shortLink.getExpiresAt(),
                shortLink.isActive());
        // Populate the cache on the way out, same as any read-through cache-aside
        // implementation. Wrapped in the Circuit Breaker inside ShortLinkCache itself, so a
        // Redis outage here is silently skipped rather than failing this request.
        cache.put(shortCode, cached);
        return cached;
    }

    private boolean isExpired(OffsetDateTime expiresAt) {
        return expiresAt != null && expiresAt.isBefore(OffsetDateTime.now());
    }

    private String pickTargetUrl(CachedShortLink resolved, RedirectDeviceType deviceType) {
        List<RedirectRule> rules = resolved.redirectRules();
        if (rules == null || rules.isEmpty()) {
            return resolved.longUrl();
        }
        return rules.stream()
                .filter(rule -> rule.deviceType() == deviceType)
                .findFirst()
                .or(() -> rules.stream()
                        .filter(rule -> rule.deviceType() == RedirectDeviceType.DEFAULT)
                        .findFirst())
                .map(RedirectRule::targetUrl)
                .orElse(resolved.longUrl());
    }

    private String writeRedirectRules(List<RedirectRule> redirectRules) {
        if (redirectRules == null || redirectRules.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(redirectRules);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize redirectRules", e);
        }
    }

    private List<RedirectRule> readRedirectRules(String redirectRulesJson) {
        if (redirectRulesJson == null || redirectRulesJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(redirectRulesJson, new TypeReference<List<RedirectRule>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize redirectRules", e);
        }
    }
}
