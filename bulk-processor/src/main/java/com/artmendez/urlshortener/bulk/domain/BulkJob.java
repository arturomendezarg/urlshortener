package com.artmendez.urlshortener.bulk.domain;

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
 * This module's own JPA mapping of {@code bulk_jobs} (created by v2-shortener-service's
 * Liquibase changelog). Unlike that service's read/create-only entity of the same name, THIS
 * one is the write side: {@link #markProcessing()}, {@link #recordItemOutcome(boolean)} and
 * {@link #complete()} are the only way this module ever changes a job's state, deliberately
 * narrow so {@code BulkJobProcessingService} cannot set status/counters to anything but one of
 * these well-defined transitions.
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

    public Long getId() {
        return id;
    }

    public String getOwnerUserId() {
        return ownerUserId;
    }

    public BulkJobStatus getStatus() {
        return status;
    }

    public int getProcessedItems() {
        return processedItems;
    }

    public int getFailedItems() {
        return failedItems;
    }

    public int getTotalItems() {
        return totalItems;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    /** True once this job has reached a terminal state and needs no further processing. */
    public boolean isTerminal() {
        return status == BulkJobStatus.COMPLETED
                || status == BulkJobStatus.COMPLETED_WITH_ERRORS
                || status == BulkJobStatus.FAILED;
    }

    public void markProcessing() {
        this.status = BulkJobStatus.PROCESSING;
    }

    public void recordItemOutcome(boolean succeeded) {
        if (succeeded) {
            processedItems++;
        } else {
            failedItems++;
        }
    }

    /** Derives the final status from how many items succeeded vs. failed, and stamps {@code completedAt}. */
    public void complete() {
        this.completedAt = OffsetDateTime.now();
        if (failedItems == 0) {
            this.status = BulkJobStatus.COMPLETED;
        } else if (processedItems == 0) {
            this.status = BulkJobStatus.FAILED;
        } else {
            this.status = BulkJobStatus.COMPLETED_WITH_ERRORS;
        }
    }
}
