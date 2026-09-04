package com.artmendez.urlshortener.v1.service;

import com.artmendez.urlshortener.v1.domain.UrlRecord;
import com.artmendez.urlshortener.v1.repository.UrlRecordRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.OffsetDateTime;

/**
 * Business logic of Monolith V1.
 *
 * <p>The code generator is deliberately simple (alphanumeric alphabet + SecureRandom +
 * retry on collision), unlike the Base62 generator with formal collision handling
 * that will be implemented for V2 (see ARCHITECTURE.md, section 6, Scenario A / Task #4). This
 * simplicity is intentional: V1 represents the "legacy" system as it was built before
 * the redesign, not the ideal solution.
 *
 * <p>Brownfield scenario (Task #5): {@code createShortUrl(String)} is preserved unchanged
 * (it delegates with {@code expiresAt = null}, i.e. "never expires") and {@code resolve} now
 * checks for expiration before returning the record.
 */
@Service
public class UrlShortenerService {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int CODE_LENGTH = 6;
    private static final int MAX_ATTEMPTS = 5;

    private final UrlRecordRepository repository;
    private final SecureRandom random = new SecureRandom();

    public UrlShortenerService(UrlRecordRepository repository) {
        this.repository = repository;
    }

    public UrlRecord createShortUrl(String longUrl) {
        return createShortUrl(longUrl, null);
    }

    public UrlRecord createShortUrl(String longUrl, OffsetDateTime expiresAt) {
        String shortCode = generateUniqueCode();
        UrlRecord record = new UrlRecord(shortCode, longUrl, OffsetDateTime.now(), expiresAt);
        return repository.save(record);
    }

    public UrlRecord resolve(String shortCode) {
        UrlRecord record = repository.findByShortCode(shortCode)
                .orElseThrow(() -> new ShortCodeNotFoundException(shortCode));
        if (record.isExpired(OffsetDateTime.now())) {
            throw new UrlExpiredException(shortCode);
        }
        return record;
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = randomCode();
            if (!repository.existsByShortCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Could not generate a unique short code after " + MAX_ATTEMPTS + " attempts");
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
