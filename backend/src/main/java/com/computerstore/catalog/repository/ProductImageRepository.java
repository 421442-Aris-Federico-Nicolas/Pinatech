package com.computerstore.catalog.repository;

import com.computerstore.catalog.domain.ProductImage;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {
    long countByProductId(Long productId);
    Optional<ProductImage> findFirstByProductIdOrderByDisplayOrderDesc(Long productId);
    List<ProductImage> findByProductIdOrderByDisplayOrderAsc(Long productId);
    @EntityGraph(attributePaths = "product")
    List<ProductImage> findByProductIdInOrderByProductIdAscDisplayOrderAsc(Collection<Long> productIds);
    Optional<ProductImage> findByIdAndProductId(Long id, Long productId);
    long countByProductIdAndIdIn(Long productId, Collection<Long> ids);
    Optional<ProductImage> findByIdAndProductActiveTrue(Long id);
}
