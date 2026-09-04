package com.artmendez.urlshortener.v2.shortlink.service;

import com.artmendez.urlshortener.v2.shortlink.cache.CachedShortLink;
import com.artmendez.urlshortener.v2.shortlink.cache.ShortLinkCache;
import com.artmendez.urlshortener.v2.shortlink.domain.RedirectDeviceType;
import com.artmendez.urlshortener.v2.shortlink.domain.RedirectRule;
import com.artmendez.urlshortener.v2.shortlink.domain.ShortLink;
import com.artmendez.urlshortener.v2.shortlink.repository.ShortLinkRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShortLinkServiceTest {

    private ShortLinkRepository repository;
    private ShortLinkCache cache;
    private ReservedSlugs reservedSlugs;
    private ShortLinkService service;

    @BeforeEach
    void setUp() {
        repository = mock(ShortLinkRepository.class);
        cache = mock(ShortLinkCache.class);
        reservedSlugs = new ReservedSlugs(List.of("api", "admin", "health", "actuator"));
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        service = new ShortLinkService(repository, cache, reservedSlugs, objectMapper);

        when(repository.save(any(ShortLink.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void create_generatesACodeWhenNoCustomAliasIsGiven() {
        when(repository.existsByShortCode(anyString())).thenReturn(false);

        ShortLink created = service.create("https://example.com/page", null, null, null, "user-123");

        assertThat(created.getShortCode()).hasSizeGreaterThanOrEqualTo(7);
        assertThat(created.getLongUrl()).isEqualTo("https://example.com/page");
        assertThat(created.getOwnerUserId()).isEqualTo("user-123");
        assertThat(created.isActive()).isTrue();
    }

    @Test
    void create_usesTheCustomAliasWhenAvailable() {
        when(repository.existsByShortCode("my-alias")).thenReturn(false);

        ShortLink created = service.create("https://example.com", "my-alias", null, null, "user-123");

        assertThat(created.getShortCode()).isEqualTo("my-alias");
    }

    @Test
    void create_rejectsAReservedSlugAsCustomAlias() {
        assertThatThrownBy(() -> service.create("https://example.com", "admin", null, null, "user-123"))
                .isInstanceOf(ReservedSlugException.class);
    }

    @Test
    void create_rejectsACustomAliasAlreadyInUse() {
        when(repository.existsByShortCode("taken")).thenReturn(true);

        assertThatThrownBy(() -> service.create("https://example.com", "taken", null, null, "user-123"))
                .isInstanceOf(DuplicateAliasException.class);
    }

    @Test
    void create_rejectsAnInvalidLongUrlBeforeTouchingTheRepository() {
        assertThatThrownBy(() -> service.create("ftp://example.com/file", null, null, null, "user-123"))
                .isInstanceOf(InvalidLongUrlException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void create_persistsRedirectRulesAsJson() {
        when(repository.existsByShortCode(anyString())).thenReturn(false);
        List<RedirectRule> rules = List.of(new RedirectRule(RedirectDeviceType.MOBILE, "https://m.example.com"));

        ShortLink created = service.create("https://example.com", null, null, rules, "user-123");

        assertThat(created.getRedirectRulesJson()).contains("MOBILE").contains("m.example.com");
    }

    @Test
    void resolve_throwsNotFoundWhenNeitherCacheNorRepositoryHaveTheCode() {
        when(cache.get("missing")).thenReturn(Optional.empty());
        when(repository.findByShortCode("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve("missing", "any-agent"))
                .isInstanceOf(ShortLinkNotFoundException.class);
    }

    @Test
    void resolve_returnsLongUrlOnACacheHitWithNoRedirectRules() {
        CachedShortLink cached = new CachedShortLink("https://example.com/target", List.of(), null, true);
        when(cache.get("abc1234")).thenReturn(Optional.of(cached));

        String target = service.resolve("abc1234", "Mozilla/5.0 (Windows NT 10.0)");

        assertThat(target).isEqualTo("https://example.com/target");
    }

    @Test
    void resolve_loadsFromRepositoryOnACacheMissAndPopulatesTheCache() {
        ShortLink entity = new ShortLink(
                "abc1234", "https://example.com/target", "user-123", null, OffsetDateTime.now(), null);
        when(cache.get("abc1234")).thenReturn(Optional.empty());
        when(repository.findByShortCode("abc1234")).thenReturn(Optional.of(entity));

        String target = service.resolve("abc1234", null);

        assertThat(target).isEqualTo("https://example.com/target");
        ArgumentCaptor<CachedShortLink> captor = ArgumentCaptor.forClass(CachedShortLink.class);
        verify(cache).put(org.mockito.ArgumentMatchers.eq("abc1234"), captor.capture());
        assertThat(captor.getValue().longUrl()).isEqualTo("https://example.com/target");
    }

    @Test
    void resolve_throwsExpiredForALinkPastItsExpiresAt() {
        CachedShortLink cached = new CachedShortLink(
                "https://example.com/target", List.of(), OffsetDateTime.now().minusDays(1), true);
        when(cache.get("expired1")).thenReturn(Optional.of(cached));

        assertThatThrownBy(() -> service.resolve("expired1", null))
                .isInstanceOf(ShortLinkExpiredException.class);
    }

    @Test
    void resolve_throwsExpiredForADeactivatedLinkEvenIfNotPastExpiresAt() {
        CachedShortLink cached = new CachedShortLink("https://example.com/target", List.of(), null, false);
        when(cache.get("inactive1")).thenReturn(Optional.of(cached));

        assertThatThrownBy(() -> service.resolve("inactive1", null))
                .isInstanceOf(ShortLinkExpiredException.class);
    }

    @Test
    void resolve_appliesTheRuleMatchingTheClassifiedDeviceType() {
        List<RedirectRule> rules = List.of(
                new RedirectRule(RedirectDeviceType.MOBILE, "https://m.example.com"),
                new RedirectRule(RedirectDeviceType.DESKTOP, "https://desktop.example.com"));
        CachedShortLink cached = new CachedShortLink("https://example.com/default", rules, null, true);
        when(cache.get("smart1")).thenReturn(Optional.of(cached));

        String target = service.resolve(
                "smart1", "Mozilla/5.0 (Linux; Android 14; Pixel 8) Mobile Safari/537.36");

        assertThat(target).isEqualTo("https://m.example.com");
    }

    @Test
    void resolve_fallsBackToTheDefaultRuleWhenNoRuleMatchesTheDevice() {
        List<RedirectRule> rules = List.of(
                new RedirectRule(RedirectDeviceType.MOBILE, "https://m.example.com"),
                new RedirectRule(RedirectDeviceType.DEFAULT, "https://fallback.example.com"));
        CachedShortLink cached = new CachedShortLink("https://example.com/default", rules, null, true);
        when(cache.get("smart2")).thenReturn(Optional.of(cached));

        String target = service.resolve(
                "smart2", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0");

        assertThat(target).isEqualTo("https://fallback.example.com");
    }

    @Test
    void resolve_fallsBackToLongUrlWhenNoRuleMatchesAndThereIsNoDefaultRule() {
        List<RedirectRule> rules = List.of(new RedirectRule(RedirectDeviceType.MOBILE, "https://m.example.com"));
        CachedShortLink cached = new CachedShortLink("https://example.com/default", rules, null, true);
        when(cache.get("smart3")).thenReturn(Optional.of(cached));

        String target = service.resolve(
                "smart3", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0");

        assertThat(target).isEqualTo("https://example.com/default");
    }
}
