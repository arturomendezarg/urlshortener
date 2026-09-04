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
 * Entidad JPA que representa una URL corta en el Monolito V1.
 *
 * <p>Nota deliberada: esta entidad NO tiene un campo {@code expiresAt}. Esa columna se agrega
 * en el Dia 2 (escenario Brownfield) mediante un changelog de Liquibase adicional, sin romper
 * el esquema existente ni requerir downtime (ver ARCHITECTURE.md, seccion 6, Escenario B).
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

    protected UrlRecord() {
        // Requerido por JPA.
    }

    public UrlRecord(String shortCode, String longUrl, OffsetDateTime createdAt) {
        this.shortCode = shortCode;
        this.longUrl = longUrl;
        this.createdAt = createdAt;
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
}
