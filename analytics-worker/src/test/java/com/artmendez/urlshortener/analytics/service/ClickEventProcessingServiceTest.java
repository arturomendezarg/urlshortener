package com.artmendez.urlshortener.analytics.service;

import com.artmendez.urlshortener.analytics.domain.ClickEventRecord;
import com.artmendez.urlshortener.analytics.messaging.ClickEventMessage;
import com.artmendez.urlshortener.analytics.repository.ClickEventRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ClickEventProcessingServiceTest {

    @Mock
    private ClickEventRepository repository;

    @Test
    void anonymizesIpAndClassifiesDeviceBeforePersisting() {
        ClickEventProcessingService service = new ClickEventProcessingService(repository);
        ClickEventMessage message = new ClickEventMessage(
                "abc123",
                "v1",
                OffsetDateTime.parse("2026-09-04T10:15:30Z"),
                "192.168.1.45",
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)",
                "https://example.com");

        service.process(message);

        ArgumentCaptor<ClickEventRecord> captor = ArgumentCaptor.forClass(ClickEventRecord.class);
        verify(repository).save(captor.capture());
        ClickEventRecord saved = captor.getValue();

        assertThat(saved.getShortCode()).isEqualTo("abc123");
        assertThat(saved.getServiceOrigin()).isEqualTo("v1");
        assertThat(saved.getAnonymizedIp()).isEqualTo("192.168.1.0");
        assertThat(saved.getDeviceType()).isEqualTo(DeviceType.MOBILE);
        assertThat(saved.getReferrer()).isEqualTo("https://example.com");
    }

    @Test
    void neverPersistsTheRawClientIp() {
        ClickEventProcessingService service = new ClickEventProcessingService(repository);
        ClickEventMessage message = new ClickEventMessage(
                "xyz789", "v1", OffsetDateTime.now(), "203.0.113.99", "curl/8.0", null);

        service.process(message);

        ArgumentCaptor<ClickEventRecord> captor = ArgumentCaptor.forClass(ClickEventRecord.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getAnonymizedIp()).isNotEqualTo("203.0.113.99");
    }

    @Test
    void classifiesMissingUserAgentAsUnknownDevice() {
        ClickEventProcessingService service = new ClickEventProcessingService(repository);
        ClickEventMessage message = new ClickEventMessage(
                "noagent", "v1", OffsetDateTime.now(), "203.0.113.99", null, null);

        service.process(message);

        ArgumentCaptor<ClickEventRecord> captor = ArgumentCaptor.forClass(ClickEventRecord.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getDeviceType()).isEqualTo(DeviceType.UNKNOWN);
    }
}
