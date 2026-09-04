package com.artmendez.urlshortener.v2.shortlink.bulk.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One line of a bulk submission (ARCHITECTURE.md, section 4.2; Day 3, Task #10). {@code
 * customAlias} is persisted here even though it is not in the summarized column list in section
 * 4.2 — see the changelog comment in {@code changelog-v2.0-bulk-jobs.xml} for why. Read/create
 * only from this service's side, same rationale as {@link BulkJob}.
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

    public BulkJobItem(Long bulkJobId, int lineIndex, String longUrl, String customAlias) {
        this.bulkJobId = bulkJobId;
        this.lineIndex = lineIndex;
        this.longUrl = longUrl;
        this.customAlias = customAlias;
        this.status = BulkJobItemStatus.PENDING;
    }

    public Long getId() {
        return id;
    }

    public Long getBulkJobId() {
        return bulkJobId;
    }

    public int getLineIndex() {
        return lineIndex;
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

    public String getShortCode() {
        return shortCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
