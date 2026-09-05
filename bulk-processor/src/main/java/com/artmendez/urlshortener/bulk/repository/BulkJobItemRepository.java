package com.artmendez.urlshortener.bulk.repository;

import com.artmendez.urlshortener.bulk.domain.BulkJobItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BulkJobItemRepository extends JpaRepository<BulkJobItem, Long> {

    List<BulkJobItem> findByBulkJobIdOrderByLineIndexAsc(Long bulkJobId);
}
