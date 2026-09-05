package com.artmendez.urlshortener.bulk.repository;

import com.artmendez.urlshortener.bulk.domain.BulkJob;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BulkJobRepository extends JpaRepository<BulkJob, Long> {
}
