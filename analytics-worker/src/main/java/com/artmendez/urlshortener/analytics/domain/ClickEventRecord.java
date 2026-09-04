package com.artmendez.urlshortener.analytics.domain;

import com.artmendez.urlshortener.analytics.service.DeviceType;

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
 * JPA entity for the append-only {@code click_events} table (see ARCHITECTURE.md, section
 * 4.2).
 *
 * <p>Rows are written exactly once, by this worker, after processing a message from the
 * {@code click-events} queue: the IP is already anonymized and the device type already
 * classified by the time an instance of this entity is built (see
 * {@link com.artmendez.urlshortener.analytics.service.ClickEventProcessingService}) — this
 * class holds no anonymization or classification logic of its own, only persistence mapping.
 */
@Entity
@Table(name = "click_events")
public class ClickEventRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "short_code", nullable = false, length = 20)
    private String shortCode;

    @Column(name = "service_origin", nullable = false, length = 20)
    private String serviceOrigin;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "anonymized_ip", length = 45)
    private String anonymizedIp;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", length = 20)
    private DeviceType deviceType;

    @Column(name = "referrer", columnDefinition = "TEXT")
    private String referrer;

    protected ClickEventRecord() {
        // Required by JPA.
    }

    public ClickEventRecord(
            String shortCode,
            String serviceOrigin,
            OffsetDateTime occurredAt,
            String anonymizedIp,
            String userAgent,
            DeviceType deviceType,
            String referrer) {
        this.shortCode = shortCode;
        this.serviceOrigin = serviceOrigin;
        this.occurredAt = occurredAt;
        this.anonymizedIp = anonymizedIp;
        this.userAgent = userAgent;
        this.deviceType = deviceType;
        this.referrer = referrer;
    }

    public Long getId() {
        return id;
    }

    public String getShortCode() {
        return shortCode;
    }

    public String getServiceOrigin() {
        return serviceOrigin;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public String getAnonymizedIp() {
        return anonymizedIp;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public DeviceType getDeviceType() {
        return deviceType;
    }

    public String getReferrer() {
        return referrer;
    }
}
