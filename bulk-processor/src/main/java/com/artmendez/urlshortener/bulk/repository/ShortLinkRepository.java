package com.artmendez.urlshortener.bulk.repository;

import com.artmendez.urlshortener.bulk.domain.ShortLink;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShortLinkRepository extends JpaRepository<ShortLink, Long> {

    boolean existsByShortCode(String shortCode);
}
