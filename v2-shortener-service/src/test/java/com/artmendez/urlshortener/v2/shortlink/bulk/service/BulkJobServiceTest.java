package com.artmendez.urlshortener.v2.shortlink.bulk.service;

import com.artmendez.urlshortener.v2.shortlink.bulk.domain.BulkJob;
import com.artmendez.urlshortener.v2.shortlink.bulk.domain.BulkJobItem;
import com.artmendez.urlshortener.v2.shortlink.bulk.domain.BulkJobStatus;
import com.artmendez.urlshortener.v2.shortlink.bulk.messaging.BulkJobMessage;
import com.artmendez.urlshortener.v2.shortlink.bulk.messaging.BulkJobPublisher;
import com.artmendez.urlshortener.v2.shortlink.bulk.repository.BulkJobItemRepository;
import com.artmendez.urlshortener.v2.shortlink.bulk.repository.BulkJobRepository;
import com.artmendez.urlshortener.v2.shortlink.bulk.web.BulkUrlItemRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BulkJobServiceTest {

    private static final int MAX_ITEMS = 3;

    private BulkJobRepository jobRepository;
    private BulkJobItemRepository itemRepository;
    private BulkJobPublisher publisher;
    private BulkJobService service;

    @BeforeEach
    void setUp() {
        jobRepository = mock(BulkJobRepository.class);
        itemRepository = mock(BulkJobItemRepository.class);
        publisher = mock(BulkJobPublisher.class);
        service = new BulkJobService(jobRepository, itemRepository, publisher, MAX_ITEMS);

        when(jobRepository.save(any(BulkJob.class))).thenAnswer(invocation -> {
            BulkJob job = invocation.getArgument(0);
            ReflectionTestUtils.setField(job, "id", 42L);
            return job;
        });
    }

    @SuppressWarnings("unchecked")
    @Test
    void createJob_savesJobAndItemsAndPublishesAMessage() {
        List<BulkUrlItemRequest> urls = List.of(
                new BulkUrlItemRequest("https://example.com/1", null),
                new BulkUrlItemRequest("https://example.com/2", "custom"));

        BulkJob job = service.createJob(urls, "user-123");

        assertThat(job.getId()).isEqualTo(42L);
        assertThat(job.getOwnerUserId()).isEqualTo("user-123");
        assertThat(job.getTotalItems()).isEqualTo(2);
        assertThat(job.getStatus()).isEqualTo(BulkJobStatus.PENDING);

        ArgumentCaptor<List<BulkJobItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(itemRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        assertThat(captor.getValue().get(0).getLineIndex()).isEqualTo(0);
        assertThat(captor.getValue().get(1).getCustomAlias()).isEqualTo("custom");

        verify(publisher).publish(new BulkJobMessage(42L));
    }

    @Test
    void createJob_rejectsSubmissionsLargerThanTheConfiguredMax() {
        List<BulkUrlItemRequest> urls = List.of(
                new BulkUrlItemRequest("https://example.com/1", null),
                new BulkUrlItemRequest("https://example.com/2", null),
                new BulkUrlItemRequest("https://example.com/3", null),
                new BulkUrlItemRequest("https://example.com/4", null));

        assertThatThrownBy(() -> service.createJob(urls, "user-123"))
                .isInstanceOf(BulkSubmissionTooLargeException.class);

        verify(jobRepository, never()).save(any());
        verify(publisher, never()).publish(any());
    }

    @Test
    void createJob_propagatesAPublishFailureRatherThanSwallowingIt() {
        List<BulkUrlItemRequest> urls = List.of(new BulkUrlItemRequest("https://example.com/1", null));
        doThrow(new AmqpException("broker down")).when(publisher).publish(any());

        assertThatThrownBy(() -> service.createJob(urls, "user-123")).isInstanceOf(AmqpException.class);
    }

    @Test
    void getJob_throwsNotFoundForAMissingJob() {
        when(jobRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getJob(99L, "user-123")).isInstanceOf(BulkJobNotFoundException.class);
    }

    @Test
    void getJob_throwsNotFoundWhenRequestedByADifferentOwner() {
        BulkJob job = new BulkJob("owner-a", 1);
        ReflectionTestUtils.setField(job, "id", 5L);
        when(jobRepository.findById(5L)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> service.getJob(5L, "owner-b")).isInstanceOf(BulkJobNotFoundException.class);
    }

    @Test
    void getJob_returnsTheJobForItsOwner() {
        BulkJob job = new BulkJob("owner-a", 1);
        ReflectionTestUtils.setField(job, "id", 5L);
        when(jobRepository.findById(5L)).thenReturn(Optional.of(job));

        assertThat(service.getJob(5L, "owner-a")).isSameAs(job);
    }
}
