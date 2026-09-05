package com.artmendez.urlshortener.v2.shortlink.bulk.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Header row of a bulk creation request (ARCHITECTURE.md, section 4.2; Day 3, Task #10).
 *
 * <p>Owned (created) by v2-shortener-service's {@code POST /api/v2/urls/bulk}, but also updated
 * in place by the separate bulk-processor module as it works through the job — a two-writer
 * table by design (see the changelog comment in {@code changelog-v2.0-bulk-jobs.xml}). This
 * entity is intentionally read/create-only from this service's side: it never mutates status,
 * counters, or {@code completedAt} after creation, so it exposes no setters. bulk-processor maps
 * its own, separately-owned {@code BulkJob} entity onto the same table for the mutations it
 * needs — the same "consumer-declares, producer-declares its own view" split already used for
 * {@code click-events} (compare {@code v1-legacy-monolith}'s {@code ClickEvent} vs.
 * {@code analytics-worker}'s {@code ClickEventMessage}).
 */
@Entity
@Table(name = "bulk_jobs")
public class BulkJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private String ownerUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private BulkJobStatus status;

    @Column(name = "total_items", nullable = false)
    private int totalItems;

    @Column(name = "processed_items", nullable = false)
    private int processedItems;

    @Column(name = "failed_items", nullable = false)
    private int failedItems;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    protected BulkJob() {
        // JPA
    }

    public BulkJob(String ownerUserId, int totalItems) {
        this.ownerUserId = ownerUserId;
        this.totalItems = totalItems;
        this.status = BulkJobStatus.PENDING;
        this.processedItems = 0;
        this.failedItems = 0;
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getOwnerUserId() {
        return ownerUserId;
    }

    public BulkJobStatus getStatus() {
        return status;
    }

    public int getTotalItems() {
        return totalItems;
    }

    public int getProcessedItems() {
        return processedItems;
    }

    public int getFailedItems() {
        return failedItems;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }
}
