package com.artmendez.urlshortener.analytics.service;

import com.artmendez.urlshortener.analytics.domain.ClickEventRecord;
import com.artmendez.urlshortener.analytics.messaging.ClickEventMessage;
import com.artmendez.urlshortener.analytics.repository.ClickEventRepository;

import org.springframework.stereotype.Service;

/**
 * Turns a raw {@link ClickEventMessage} off the {@code click-events} queue into a persisted,
 * privacy-safe {@link ClickEventRecord}.
 *
 * <p>Two transformations happen here, and nowhere else, before a row is written: the client IP
 * is anonymized ({@link IpAnonymizer}) and the device type is inferred from the user agent
 * ({@link DeviceTypeClassifier}). The raw, non-anonymized IP carried by the message is never
 * persisted or logged by this class.
 */
@Service
public class ClickEventProcessingService {

    private final ClickEventRepository repository;

    public ClickEventProcessingService(ClickEventRepository repository) {
        this.repository = repository;
    }

    public void process(ClickEventMessage message) {
        String anonymizedIp = IpAnonymizer.anonymize(message.clientIp());
        DeviceType deviceType = DeviceTypeClassifier.classify(message.userAgent());

        ClickEventRecord record = new ClickEventRecord(
                message.shortCode(),
                message.serviceOrigin(),
                message.occurredAt(),
                anonymizedIp,
                message.userAgent(),
                deviceType,
                message.referrer());

        repository.save(record);
    }
}
