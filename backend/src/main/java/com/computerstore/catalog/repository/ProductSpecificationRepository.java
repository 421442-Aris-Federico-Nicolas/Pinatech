package com.computerstore.catalog.repository;

import java.util.Collection;
import java.util.List;

import com.computerstore.catalog.domain.ProductSpecification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ProductSpecificationRepository extends JpaRepository<ProductSpecification, Long> {
    List<ProductSpecification> findByProduct_IdOrderByDisplayOrderAscIdAsc(Long productId);

    @Query("select s from ProductSpecification s where s.product.id in :productIds order by s.product.id, s.displayOrder, s.id")
    List<ProductSpecification> findOrderedByProductIds(Collection<Long> productIds);

    @Modifying
    @Query("delete from ProductSpecification s where s.product.id = :productId")
    void deleteByProductId(Long productId);
}
