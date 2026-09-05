package com.artmendez.urlshortener.bulk.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Minimal write-only mapping of {@code short_links} (created by v2-shortener-service's Task #9
 * changelog), covering only the columns a bulk-created link needs: no {@code redirectRules}
 * (bulk items don't accept per-device rules, see {@code CreateBulkUrlRequest} in
 * v2-shortener-service) and no {@code expiresAt} (same reason — not part of the bulk request
 * shape). Both are nullable in the real table, so Hibernate's {@code ddl-auto: validate} is
 * satisfied by this narrower mapping; it only requires every column THIS entity maps to exist
 * with a compatible type, not that the entity covers every column of the table.
 */
@Entity
@Table(name = "short_links")
public class ShortLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "short_code", nullable = false, unique = true, length = 20)
    private String shortCode;

    @Column(name = "long_url", nullable = false, columnDefinition = "TEXT")
    private String longUrl;

    @Column(name = "owner_user_id")
    private String ownerUserId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    protected ShortLink() {
        // JPA
    }

    public ShortLink(String shortCode, String longUrl, String ownerUserId, OffsetDateTime createdAt) {
        this.shortCode = shortCode;
        this.longUrl = longUrl;
        this.ownerUserId = ownerUserId;
        this.createdAt = createdAt;
        this.active = true;
    }

    public String getShortCode() {
        return shortCode;
    }

    public Long getId() {
        return id;
    }

    public String getLongUrl() {
        return longUrl;
    }

    public String getOwnerUserId() {
        return ownerUserId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isActive() {
        return active;
    }
}
