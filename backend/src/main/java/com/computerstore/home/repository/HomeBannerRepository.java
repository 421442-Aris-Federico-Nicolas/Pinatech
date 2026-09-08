package com.computerstore.home.repository;

import com.computerstore.home.domain.HomeBanner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HomeBannerRepository extends JpaRepository<HomeBanner, Long> {
    Optional<HomeBanner> findByIdAndSectionActiveTrue(Long id);
}
