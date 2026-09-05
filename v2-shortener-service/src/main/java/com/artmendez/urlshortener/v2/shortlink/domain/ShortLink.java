package com.artmendez.urlshortener.v2.shortlink.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * Persistent short link created through {@code POST /api/v2/urls} and resolved through
 * {@code GET /{shortCode}} (see ARCHITECTURE.md, section 4.2 for the data model and section 6,
 * Scenario A/C for the create/redirect behavior).
 *
 * <p>{@code redirectRulesJson} stores the {@code RedirectRule} list from the OpenAPI contract as
 * a raw JSON string, mapped directly onto a {@code jsonb} column via Hibernate 6's
 * {@code @JdbcTypeCode(SqlTypes.JSON)} — no extra library needed, and no separate
 * {@code redirect_rules} table: the rule list is small, always read/written as a whole with its
 * parent link, and never queried on its own, so a normalized table would add joins for no
 * benefit. Serialization/deserialization into {@link RedirectRule} happens in the service layer
 * ({@code ShortLinkService}), keeping this entity a thin persistence mapping.
 *
 * <p>{@code ownerUserId} is the raw Keycloak {@code sub} claim (see the changelog comment in
 * {@code changelog-v1.0-init.xml} for why this is not a foreign key to an {@code app_user}
 * table).
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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "redirect_rules", columnDefinition = "jsonb")
    private String redirectRulesJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    protected ShortLink() {
        // JPA
    }

    public ShortLink(
            String shortCode,
            String longUrl,
            String ownerUserId,
            String redirectRulesJson,
            OffsetDateTime createdAt,
            OffsetDateTime expiresAt) {
        this.shortCode = shortCode;
        this.longUrl = longUrl;
        this.ownerUserId = ownerUserId;
        this.redirectRulesJson = redirectRulesJson;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.active = true;
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

    public String getOwnerUserId() {
        return ownerUserId;
    }

    public String getRedirectRulesJson() {
        return redirectRulesJson;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public boolean isActive() {
        return active;
    }
}
