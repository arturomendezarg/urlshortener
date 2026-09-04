package com.artmendez.urlshortener.v1.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.OffsetDateTime;

/**
 * JPA entity representing a short URL in Monolith V1.
 *
 * <p>Brownfield scenario (see ARCHITECTURE.md, section 6, Scenario B): the {@code expiresAt}
 * field was added via an additional Liquibase changelog (nullable column, no default value),
 * preserving the existing schema and rows. {@code expiresAt == null} means "the URL never
 * expires" — this is exactly the behavior of every row created before this change, so it
 * requires no backfill and no downtime.
 */
@Entity
@Table(name = "urls", uniqueConstraints = @UniqueConstraint(name = "uk_urls_short_code", columnNames = "short_code"))
public class UrlRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "short_code", nullable = false, length = 20)
    private String shortCode;

    @Column(name = "long_url", nullable = false, columnDefinition = "TEXT")
    private String longUrl;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    protected UrlRecord() {
        // Required by JPA.
    }

    /**
     * Original constructor, preserved unchanged so as not to break existing callers
     * (including the characterization tests). Equivalent to creating a URL with no expiration.
     */
    public UrlRecord(String shortCode, String longUrl, OffsetDateTime createdAt) {
        this(shortCode, longUrl, createdAt, null);
    }

    public UrlRecord(String shortCode, String longUrl, OffsetDateTime createdAt, OffsetDateTime expiresAt) {
        this.shortCode = shortCode;
        this.longUrl = longUrl;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public String getShortCode() {
        return shortCode;
    }

    public String getLongUrl() {
        return longUrl;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    /**
     * A URL with no {@code expiresAt} (null) never expires — that is how every legacy row
     * behaved before this change, and that behavior is explicitly preserved here.
     */
    public boolean isExpired(OffsetDateTime now) {
        return expiresAt != null && now.isAfter(expiresAt);
    }
}
