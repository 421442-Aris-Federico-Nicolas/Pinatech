package com.computerstore.home.repository;

import com.computerstore.home.domain.HomeHeroSlide;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface HomeHeroSlideRepository extends JpaRepository<HomeHeroSlide, Long> {
    List<HomeHeroSlide> findAllByOrderByDisplayOrderAscIdAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select slide from HomeHeroSlide slide where slide.id = :id")
    Optional<HomeHeroSlide> findByIdForUpdate(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select slide from HomeHeroSlide slide order by slide.displayOrder, slide.id")
    List<HomeHeroSlide> findAllForUpdate();

    long countByActiveTrue();

    @Query("select coalesce(max(slide.displayOrder), -1) from HomeHeroSlide slide")
    int maximumDisplayOrder();
}