package com.artmendez.urlshortener.v1.repository;

import com.artmendez.urlshortener.v1.domain.UrlRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UrlRecordRepository extends JpaRepository<UrlRecord, Long> {

    Optional<UrlRecord> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);
}
