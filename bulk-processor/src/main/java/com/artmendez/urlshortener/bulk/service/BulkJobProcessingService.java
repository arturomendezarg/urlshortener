package com.artmendez.urlshortener.bulk.service;

import com.artmendez.urlshortener.bulk.domain.BulkJob;
import com.artmendez.urlshortener.bulk.domain.BulkJobItem;
import com.artmendez.urlshortener.bulk.domain.ShortLink;
import com.artmendez.urlshortener.bulk.repository.BulkJobItemRepository;
import com.artmendez.urlshortener.bulk.repository.BulkJobRepository;
import com.artmendez.urlshortener.bulk.repository.ShortLinkRepository;
import com.artmendez.urlshortener.v2.codec.Base62CodeGenerator;
import com.artmendez.urlshortener.v2.validation.InvalidLongUrlException;
import com.artmendez.urlshortener.v2.validation.LongUrlValidator;
import com.artmendez.urlshortener.v2.validation.ReservedSlugs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Core logic triggered by {@link com.artmendez.urlshortener.bulk.messaging.BulkJobListener} for
 * every {@code bulk-url-jobs} message (Day 3, Task #10).
 *
 * <p><b>Idempotency:</b> the whole job is processed inside ONE transaction (see {@link
 * #process(Long)}). If anything inside it throws an exception that is NOT caught locally (a
 * genuine infrastructure failure — e.g. the database connection drops mid-loop), the entire
 * transaction rolls back: no item this delivery attempt touched is left half-committed. Spring
 * Retry then retries the whole listener invocation from a clean slate (see {@code RabbitConfig}
 * for the bounded-retry-then-dead-letter policy), and because nothing committed, that retry
 * naturally reprocesses the job correctly with no special-casing needed. On top of that,
 * {@link BulkJob#isTerminal()} and {@link BulkJobItem#isPending()} guard against the OTHER
 * redelivery scenario — the transaction fully committed but the broker redelivers anyway because
 * the ack never made it back (e.g. this instance crashed right after commit) — by making a
 * second full run of this method a no-op.
 *
 * <p><b>Per-item isolation:</b> a single line failing business validation (bad URL, reserved
 * slug, alias already taken) must not fail the other 499 lines of a 500-item submission — those
 * are caught locally in {@link #processItem(BulkJobItem, String)} and recorded as a {@code
 * FAILED} item, never rethrown. Only a truly unexpected exception is allowed to propagate and
 * trigger the whole-message retry/DLQ path described above.
 */
@Service
public class BulkJobProcessingService {

    private static final Logger log = LoggerFactory.getLogger(BulkJobProcessingService.class);

    private final BulkJobRepository jobRepository;
    private final BulkJobItemRepository itemRepository;
    private final ShortLinkRepository shortLinkRepository;
    private final ReservedSlugs reservedSlugs;
    private final Base62CodeGenerator codeGenerator = new Base62CodeGenerator();

    public BulkJobProcessingService(
            BulkJobRepository jobRepository,
            BulkJobItemRepository itemRepository,
            ShortLinkRepository shortLinkRepository,
            ReservedSlugs reservedSlugs) {
        this.jobRepository = jobRepository;
        this.itemRepository = itemRepository;
        this.shortLinkRepository = shortLinkRepository;
        this.reservedSlugs = reservedSlugs;
    }

    @Transactional
    public void process(Long jobId) {
        BulkJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            // Should not happen in practice (v2-shortener-service commits the job row before
            // publishing), but a missing job is nothing this consumer can retry its way out of.
            log.warn("Bulk job {} not found, acknowledging and skipping", jobId);
            return;
        }
        if (job.isTerminal()) {
            log.info("Bulk job {} is already {}, skipping (idempotent redelivery)", jobId, job.getStatus());
            return;
        }

        job.markProcessing();
        jobRepository.save(job);

        List<BulkJobItem> items = itemRepository.findByBulkJobIdOrderByLineIndexAsc(jobId);
        for (BulkJobItem item : items) {
            if (!item.isPending()) {
                continue;
            }
            boolean succeeded = processItem(item, job.getOwnerUserId());
            job.recordItemOutcome(succeeded);
            itemRepository.save(item);
        }

        job.complete();
        jobRepository.save(job);
        log.info(
                "Bulk job {} finished: {} processed, {} failed, final status {}",
                jobId, job.getProcessedItems(), job.getFailedItems(), job.getStatus());
    }

    /** @return {@code true} if the item succeeded, {@code false} if it was marked FAILED */
    private boolean processItem(BulkJobItem item, String ownerUserId) {
        try {
            LongUrlValidator.validate(item.getLongUrl());
            String shortCode = resolveShortCode(item.getCustomAlias());
            shortLinkRepository.save(
                    new ShortLink(shortCode, item.getLongUrl(), ownerUserId, OffsetDateTime.now()));
            item.markCompleted(shortCode);
            return true;
        } catch (InvalidLongUrlException | BulkItemProcessingException e) {
            item.markFailed(e.getMessage());
            return false;
        }
    }

    private String resolveShortCode(String customAlias) {
        if (customAlias == null || customAlias.isBlank()) {
            return codeGenerator.generate(shortLinkRepository::existsByShortCode);
        }
        if (reservedSlugs.isReserved(customAlias)) {
            throw new BulkItemProcessingException("customAlias '" + customAlias + "' is a reserved slug");
        }
        if (shortLinkRepository.existsByShortCode(customAlias)) {
            throw new BulkItemProcessingException("customAlias '" + customAlias + "' is already in use");
        }
        return customAlias;
    }
}
