package com.artmendez.urlshortener.bulk.service;

import com.artmendez.urlshortener.bulk.domain.BulkJob;
import com.artmendez.urlshortener.bulk.domain.BulkJobItem;
import com.artmendez.urlshortener.bulk.domain.BulkJobItemStatus;
import com.artmendez.urlshortener.bulk.domain.BulkJobStatus;
import com.artmendez.urlshortener.bulk.domain.ShortLink;
import com.artmendez.urlshortener.bulk.repository.BulkJobItemRepository;
import com.artmendez.urlshortener.bulk.repository.BulkJobRepository;
import com.artmendez.urlshortener.bulk.repository.ShortLinkRepository;
import com.artmendez.urlshortener.v2.validation.ReservedSlugs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mock-based unit tests for {@link BulkJobProcessingService}. The real end-to-end path (a
 * message actually consumed from RabbitMQ, real Postgres) is covered by
 * {@code BulkJobListenerIntegrationTest}; this class focuses on the per-item outcome logic,
 * job-status derivation, and the two idempotency guards described in the class's own Javadoc.
 */
class BulkJobProcessingServiceTest {

    private BulkJobRepository jobRepository;
    private BulkJobItemRepository itemRepository;
    private ShortLinkRepository shortLinkRepository;
    private BulkJobProcessingService service;

    private static BulkJob newJob(Long id, String ownerUserId, BulkJobStatus status) {
        BulkJob job = BeanUtils.instantiateClass(BulkJob.class);
        ReflectionTestUtils.setField(job, "id", id);
        ReflectionTestUtils.setField(job, "ownerUserId", ownerUserId);
        ReflectionTestUtils.setField(job, "status", status);
        ReflectionTestUtils.setField(job, "processedItems", 0);
        ReflectionTestUtils.setField(job, "failedItems", 0);
        ReflectionTestUtils.setField(job, "createdAt", OffsetDateTime.now());
        return job;
    }

    private static BulkJobItem newItem(Long bulkJobId, int lineIndex, String longUrl, String customAlias) {
        BulkJobItem item = BeanUtils.instantiateClass(BulkJobItem.class);
        ReflectionTestUtils.setField(item, "bulkJobId", bulkJobId);
        ReflectionTestUtils.setField(item, "lineIndex", lineIndex);
        ReflectionTestUtils.setField(item, "longUrl", longUrl);
        ReflectionTestUtils.setField(item, "customAlias", customAlias);
        ReflectionTestUtils.setField(item, "status", BulkJobItemStatus.PENDING);
        return item;
    }

    @BeforeEach
    void setUp() {
        jobRepository = mock(BulkJobRepository.class);
        itemRepository = mock(BulkJobItemRepository.class);
        shortLinkRepository = mock(ShortLinkRepository.class);
        ReservedSlugs reservedSlugs = new ReservedSlugs(List.of("api", "admin"));
        service = new BulkJobProcessingService(jobRepository, itemRepository, shortLinkRepository, reservedSlugs);

        when(jobRepository.save(any(BulkJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.save(any(BulkJobItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shortLinkRepository.save(any(ShortLink.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void process_isANoOpWhenTheJobNoLongerExists() {
        when(jobRepository.findById(1L)).thenReturn(Optional.empty());

        service.process(1L);

        verify(itemRepository, never()).findByBulkJobIdOrderByLineIndexAsc(any());
    }

    @Test
    void process_skipsAJobAlreadyInATerminalState() {
        BulkJob job = newJob(2L, "user-1", BulkJobStatus.COMPLETED);
        when(jobRepository.findById(2L)).thenReturn(Optional.of(job));

        service.process(2L);

        verify(itemRepository, never()).findByBulkJobIdOrderByLineIndexAsc(any());
        verify(jobRepository, never()).save(any());
    }

    @Test
    void process_createsShortLinksForEveryPendingItemAndCompletesTheJob() {
        BulkJob job = newJob(3L, "user-1", BulkJobStatus.PENDING);
        BulkJobItem item1 = newItem(3L, 0, "https://example.com/1", null);
        BulkJobItem item2 = newItem(3L, 1, "https://example.com/2", "custom-alias");
        when(jobRepository.findById(3L)).thenReturn(Optional.of(job));
        when(itemRepository.findByBulkJobIdOrderByLineIndexAsc(3L)).thenReturn(List.of(item1, item2));
        when(shortLinkRepository.existsByShortCode(anyString())).thenReturn(false);

        service.process(3L);

        assertThat(item1.getStatus()).isEqualTo(BulkJobItemStatus.COMPLETED);
        assertThat(item2.getStatus()).isEqualTo(BulkJobItemStatus.COMPLETED);
        assertThat(item2.getShortCode()).isEqualTo("custom-alias");
        assertThat(job.getStatus()).isEqualTo(BulkJobStatus.COMPLETED);
        assertThat(job.getProcessedItems()).isEqualTo(2);
        assertThat(job.getFailedItems()).isZero();
        verify(shortLinkRepository, times(2)).save(any(ShortLink.class));
    }

    @Test
    void process_marksAReservedSlugItemFailedAndStillProcessesTheRest() {
        BulkJob job = newJob(4L, "user-1", BulkJobStatus.PENDING);
        BulkJobItem badItem = newItem(4L, 0, "https://example.com/1", "admin");
        BulkJobItem goodItem = newItem(4L, 1, "https://example.com/2", null);
        when(jobRepository.findById(4L)).thenReturn(Optional.of(job));
        when(itemRepository.findByBulkJobIdOrderByLineIndexAsc(4L)).thenReturn(List.of(badItem, goodItem));
        when(shortLinkRepository.existsByShortCode(anyString())).thenReturn(false);

        service.process(4L);

        assertThat(badItem.getStatus()).isEqualTo(BulkJobItemStatus.FAILED);
        assertThat(badItem.getErrorMessage()).contains("reserved slug");
        assertThat(goodItem.getStatus()).isEqualTo(BulkJobItemStatus.COMPLETED);
        assertThat(job.getStatus()).isEqualTo(BulkJobStatus.COMPLETED_WITH_ERRORS);
        assertThat(job.getProcessedItems()).isEqualTo(1);
        assertThat(job.getFailedItems()).isEqualTo(1);
    }

    @Test
    void process_marksADuplicateAliasItemFailed() {
        BulkJob job = newJob(5L, "user-1", BulkJobStatus.PENDING);
        BulkJobItem item = newItem(5L, 0, "https://example.com/1", "taken");
        when(jobRepository.findById(5L)).thenReturn(Optional.of(job));
        when(itemRepository.findByBulkJobIdOrderByLineIndexAsc(5L)).thenReturn(List.of(item));
        when(shortLinkRepository.existsByShortCode("taken")).thenReturn(true);

        service.process(5L);

        assertThat(item.getStatus()).isEqualTo(BulkJobItemStatus.FAILED);
        assertThat(item.getErrorMessage()).contains("already in use");
    }

    @Test
    void process_marksAnInvalidLongUrlItemFailedWithoutTouchingTheRepository() {
        BulkJob job = newJob(6L, "user-1", BulkJobStatus.PENDING);
        BulkJobItem item = newItem(6L, 0, "ftp://example.com/file", null);
        when(jobRepository.findById(6L)).thenReturn(Optional.of(job));
        when(itemRepository.findByBulkJobIdOrderByLineIndexAsc(6L)).thenReturn(List.of(item));

        service.process(6L);

        assertThat(item.getStatus()).isEqualTo(BulkJobItemStatus.FAILED);
        assertThat(item.getErrorMessage()).contains("scheme");
        verify(shortLinkRepository, never()).save(any());
    }

    @Test
    void process_marksTheJobFailedWhenEveryItemFails() {
        BulkJob job = newJob(7L, "user-1", BulkJobStatus.PENDING);
        BulkJobItem item = newItem(7L, 0, "not-a-url", null);
        when(jobRepository.findById(7L)).thenReturn(Optional.of(job));
        when(itemRepository.findByBulkJobIdOrderByLineIndexAsc(7L)).thenReturn(List.of(item));

        service.process(7L);

        assertThat(job.getStatus()).isEqualTo(BulkJobStatus.FAILED);
        assertThat(job.getProcessedItems()).isZero();
        assertThat(job.getFailedItems()).isEqualTo(1);
    }

    @Test
    void process_skipsItemsThatAreAlreadyInATerminalStateFromAnEarlierPartialRun() {
        BulkJob job = newJob(8L, "user-1", BulkJobStatus.PROCESSING);
        BulkJobItem alreadyDone = newItem(8L, 0, "https://example.com/1", null);
        ReflectionTestUtils.setField(alreadyDone, "status", BulkJobItemStatus.COMPLETED);
        ReflectionTestUtils.setField(alreadyDone, "shortCode", "abc1234");
        BulkJobItem stillPending = newItem(8L, 1, "https://example.com/2", null);
        when(jobRepository.findById(8L)).thenReturn(Optional.of(job));
        when(itemRepository.findByBulkJobIdOrderByLineIndexAsc(8L)).thenReturn(List.of(alreadyDone, stillPending));
        when(shortLinkRepository.existsByShortCode(anyString())).thenReturn(false);

        service.process(8L);

        // Only the still-pending item is (re)processed: a ShortLink is created for it alone,
        // the already-COMPLETED item is left untouched.
        verify(shortLinkRepository, times(1)).save(any(ShortLink.class));
        assertThat(stillPending.getStatus()).isEqualTo(BulkJobItemStatus.COMPLETED);
        assertThat(job.getProcessedItems()).isEqualTo(1);
    }
}
