package com.artmendez.urlshortener.bulk.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * This module's own JPA mapping of {@code bulk_job_items} — the write side, mirroring
 * {@link BulkJob}'s split from v2-shortener-service's read/create-only entity of the same name.
 */
@Entity
@Table(name = "bulk_job_items")
public class BulkJobItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bulk_job_id", nullable = false)
    private Long bulkJobId;

    @Column(name = "line_index", nullable = false)
    private int lineIndex;

    @Column(name = "long_url", nullable = false, columnDefinition = "TEXT")
    private String longUrl;

    @Column(name = "custom_alias", length = 50)
    private String customAlias;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private BulkJobItemStatus status;

    @Column(name = "short_code", length = 20)
    private String shortCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    protected BulkJobItem() {
        // JPA
    }

    public Long getId() {
        return id;
    }

    public Long getBulkJobId() {
        return bulkJobId;
    }

    public String getLongUrl() {
        return longUrl;
    }

    public String getCustomAlias() {
        return customAlias;
    }

    public BulkJobItemStatus getStatus() {
        return status;
    }

    public boolean isPending() {
        return status == BulkJobItemStatus.PENDING;
    }

    public String getShortCode() {
        return shortCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void markCompleted(String shortCode) {
        this.status = BulkJobItemStatus.COMPLETED;
        this.shortCode = shortCode;
    }

    public void markFailed(String errorMessage) {
        this.status = BulkJobItemStatus.FAILED;
        this.errorMessage = errorMessage;
    }
}
