package com.artmendez.urlshortener.v2.shortlink.repository;

import com.artmendez.urlshortener.v2.shortlink.domain.ShortLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ShortLinkRepository extends JpaRepository<ShortLink, Long> {

    boolean existsByShortCode(String shortCode);

    Optional<ShortLink> findByShortCode(String shortCode);
}
