package com.computerstore.home.repository;

import com.computerstore.home.domain.HomeSection;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface HomeSectionRepository extends JpaRepository<HomeSection, Long> {
    List<HomeSection> findAllByOrderByDisplayOrderAscIdAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select section from HomeSection section where section.id = :id")
    Optional<HomeSection> findByIdForUpdate(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select section from HomeSection section order by section.displayOrder, section.id")
    List<HomeSection> findAllForUpdate();

    boolean existsByCategories_Id(Long categoryId);

    @Query(value = "select 1 from pg_advisory_xact_lock(1212504045)", nativeQuery = true)
    Integer lockConfiguration();

    @Query("select coalesce(max(section.displayOrder), -1) from HomeSection section")
    int maximumDisplayOrder();
}
