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
 * <p>Escenario Brownfield (ver ARCHITECTURE.md, seccion 6, Escenario B): se agrego el campo
 * {@code expiresAt} mediante un changelog de Liquibase adicional (columna nullable, sin valor
 * por defecto), preservando el esquema y las filas existentes. {@code expiresAt == null}
 * significa "la URL nunca expira" — es el comportamiento exacto de todas las filas creadas
 * antes de este cambio, por lo que no requiere backfill ni downtime.
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
        // Requerido por JPA.
    }

    /**
     * Constructor original, preservado sin cambios para no romper llamadores existentes
     * (incluidas las pruebas de caracterizacion). Equivale a crear una URL sin expiracion.
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
     * Una URL sin {@code expiresAt} (null) nunca expira — asi se comportaban todas las filas
     * legacy antes de este cambio, y ese comportamiento se preserva explicitamente aqui.
     */
    public boolean isExpired(OffsetDateTime now) {
        return expiresAt != null && now.isAfter(expiresAt);
    }
}
