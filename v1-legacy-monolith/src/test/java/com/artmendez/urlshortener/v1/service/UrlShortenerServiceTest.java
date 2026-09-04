package com.artmendez.urlshortener.v1.service;

import com.artmendez.urlshortener.v1.domain.UrlRecord;
import com.artmendez.urlshortener.v1.repository.UrlRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UrlShortenerServiceTest {

    @Mock
    private UrlRecordRepository repository;

    @Test
    void createShortUrl_savesRecordWithGeneratedCode() {
        UrlShortenerService service = new UrlShortenerService(repository);
        when(repository.existsByShortCode(anyString())).thenReturn(false);
        when(repository.save(any(UrlRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UrlRecord result = service.createShortUrl("https://example.com/some/long/path");

        assertThat(result.getShortCode()).hasSize(6);
        assertThat(result.getLongUrl()).isEqualTo("https://example.com/some/long/path");
        assertThat(result.getCreatedAt()).isNotNull();
    }

    @Test
    void createShortUrl_retriesOnCollisionUntilUniqueCodeFound() {
        UrlShortenerService service = new UrlShortenerService(repository);
        when(repository.existsByShortCode(anyString()))
                .thenReturn(true, true, false);
        when(repository.save(any(UrlRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.createShortUrl("https://example.com");

        verify(repository, times(3)).existsByShortCode(anyString());
    }

    @Test
    void createShortUrl_givesUpAfterMaxAttempts() {
        UrlShortenerService service = new UrlShortenerService(repository);
        when(repository.existsByShortCode(anyString())).thenReturn(true);

        assertThatThrownBy(() -> service.createShortUrl("https://example.com"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void resolve_throwsWhenShortCodeDoesNotExist() {
        UrlShortenerService service = new UrlShortenerService(repository);
        when(repository.findByShortCode("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve("missing"))
                .isInstanceOf(ShortCodeNotFoundException.class);
    }

    @Test
    void createShortUrl_singleArgOverload_delegatesWithNullExpiration() {
        UrlShortenerService service = new UrlShortenerService(repository);
        when(repository.existsByShortCode(anyString())).thenReturn(false);
        when(repository.save(any(UrlRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UrlRecord result = service.createShortUrl("https://example.com/no-expiry");

        assertThat(result.getExpiresAt()).isNull();
        assertThat(result.isExpired(OffsetDateTime.now())).isFalse();
    }

    @Test
    void resolve_throwsUrlExpiredException_whenExpirationIsInThePast() {
        UrlShortenerService service = new UrlShortenerService(repository);
        UrlRecord expired = new UrlRecord(
                "expird",
                "https://example.com/expired",
                OffsetDateTime.now().minusDays(2),
                OffsetDateTime.now().minusDays(1));
        when(repository.findByShortCode("expird")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.resolve("expird"))
                .isInstanceOf(UrlExpiredException.class);
    }

    @Test
    void resolve_returnsRecord_whenExpirationIsInTheFuture() {
        UrlShortenerService service = new UrlShortenerService(repository);
        UrlRecord notYetExpired = new UrlRecord(
                "future",
                "https://example.com/future",
                OffsetDateTime.now(),
                OffsetDateTime.now().plusDays(1));
        when(repository.findByShortCode("future")).thenReturn(Optional.of(notYetExpired));

        UrlRecord result = service.resolve("future");

        assertThat(result.getLongUrl()).isEqualTo("https://example.com/future");
    }
}
