package com.computerstore.catalog.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.computerstore.catalog.domain.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<ProductVariant> findByProduct_IdAndActiveTrueOrderByDisplayOrderAscIdAsc(Long productId);
    List<ProductVariant> findByProduct_IdOrderByDisplayOrderAscIdAsc(Long productId);
    @Lock(LockModeType.PESSIMISTIC_READ)
    Optional<ProductVariant> findByIdAndActiveTrueAndProduct_ActiveTrue(Long id);

    @Query("select variant from ProductVariant variant where variant.product.id in :productIds and variant.active order by variant.product.id, variant.displayOrder, variant.id")
    List<ProductVariant> findActiveByProductIds(Collection<Long> productIds);
}
