package com.artmendez.urlshortener.analytics.repository;

import com.artmendez.urlshortener.analytics.domain.ClickEventRecord;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ClickEventRepository extends JpaRepository<ClickEventRecord, Long> {
}
