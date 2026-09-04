package com.artmendez.urlshortener.v2.shortlink.bulk.repository;

import com.artmendez.urlshortener.v2.shortlink.bulk.domain.BulkJob;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BulkJobRepository extends JpaRepository<BulkJob, Long> {
}
