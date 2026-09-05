package com.artmendez.urlshortener.v2.shortlink.bulk.service;

import com.artmendez.urlshortener.v2.shortlink.bulk.domain.BulkJob;
import com.artmendez.urlshortener.v2.shortlink.bulk.domain.BulkJobItem;
import com.artmendez.urlshortener.v2.shortlink.bulk.messaging.BulkJobMessage;
import com.artmendez.urlshortener.v2.shortlink.bulk.messaging.BulkJobPublisher;
import com.artmendez.urlshortener.v2.shortlink.bulk.repository.BulkJobItemRepository;
import com.artmendez.urlshortener.v2.shortlink.bulk.repository.BulkJobRepository;
import com.artmendez.urlshortener.v2.shortlink.bulk.web.BulkUrlItemRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Business logic for {@code POST /api/v2/urls/bulk} and {@code GET /api/v2/urls/bulk/{jobId}}
 * (Day 3, Task #10). Deliberately does not validate each {@code longUrl} with
 * {@code LongUrlValidator} at submission time: that check resolves DNS per URL, which is cheap
 * for one URL on the synchronous create path but not something worth paying up to
 * {@code app.shortlink.bulk-max-items} times on the request thread — bulk-processor runs it
 * per item, asynchronously, off the request path, where a slow or failing resolution only
 * delays that one item's processing instead of the whole submission's HTTP response.
 */
@Service
public class BulkJobService {

    private final BulkJobRepository jobRepository;
    private final BulkJobItemRepository itemRepository;
    private final BulkJobPublisher publisher;
    private final int maxItems;

    public BulkJobService(
            BulkJobRepository jobRepository,
            BulkJobItemRepository itemRepository,
            BulkJobPublisher publisher,
            @Value("${app.shortlink.bulk-max-items}") int maxItems) {
        this.jobRepository = jobRepository;
        this.itemRepository = itemRepository;
        this.publisher = publisher;
        this.maxItems = maxItems;
    }

    /**
     * Creates the job header and its line items (both {@code PENDING}) and publishes one
     * {@link BulkJobMessage} for bulk-processor to pick up. See {@link BulkJobPublisher} for why
     * a publish failure here rolls back the whole transaction instead of leaving an orphaned row.
     *
     * @throws BulkSubmissionTooLargeException if {@code urls} has more than {@code maxItems} lines
     */
    @Transactional
    public BulkJob createJob(List<BulkUrlItemRequest> urls, String ownerUserId) {
        if (urls.size() > maxItems) {
            throw new BulkSubmissionTooLargeException(urls.size(), maxItems);
        }
        BulkJob job = jobRepository.save(new BulkJob(ownerUserId, urls.size()));

        List<BulkJobItem> items = new ArrayList<>(urls.size());
        for (int lineIndex = 0; lineIndex < urls.size(); lineIndex++) {
            BulkUrlItemRequest line = urls.get(lineIndex);
            items.add(new BulkJobItem(job.getId(), lineIndex, line.longUrl(), line.customAlias()));
        }
        itemRepository.saveAll(items);

        publisher.publish(new BulkJobMessage(job.getId()));
        return job;
    }

    /**
     * @throws BulkJobNotFoundException if no job with this id exists, or it exists but is not
     *                                   owned by {@code ownerUserId}
     */
    @Transactional(readOnly = true)
    public BulkJob getJob(Long jobId, String ownerUserId) {
        BulkJob job = jobRepository.findById(jobId).orElseThrow(() -> new BulkJobNotFoundException(jobId));
        if (!job.getOwnerUserId().equals(ownerUserId)) {
            throw new BulkJobNotFoundException(jobId);
        }
        return job;
    }

    @Transactional(readOnly = true)
    public List<BulkJobItem> getItems(Long jobId) {
        return itemRepository.findByBulkJobIdOrderByLineIndexAsc(jobId);
    }
}
