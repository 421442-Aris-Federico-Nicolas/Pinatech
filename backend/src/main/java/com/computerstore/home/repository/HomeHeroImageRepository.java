package com.computerstore.home.repository;

import com.computerstore.home.domain.HomeHeroImage;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface HomeHeroImageRepository extends JpaRepository<HomeHeroImage, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select image from HomeHeroImage image where image.id = :id")
    Optional<HomeHeroImage> findByIdForUpdate(Long id);

    @Query("""
            select image from HomeHeroImage image
            where image.id = :id and image.slide.active = true
            """)
    Optional<HomeHeroImage> findByIdAndSlideActiveTrue(Long id);
}