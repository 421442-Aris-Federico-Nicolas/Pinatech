package com.computerstore.catalog.repository;

import com.computerstore.catalog.domain.Product;
import com.computerstore.catalog.dto.ProductListItemResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    @Query(value = """
            select new com.computerstore.catalog.dto.ProductListItemResponse(
                p.id, p.name, p.slug, p.price, c.id, c.name, b.id, b.name,
                i.id, i.imageUrl, i.storageKey, i.altText, i.originalFilename, i.displayOrder,
                exists (select v.id from ProductVariant v join Inventory stock on stock.variantId = v.id
                        where v.product = p and v.active = true and stock.availableQuantity > 0))
            from Product p join p.category c join p.brand b
            left join ProductImage i on i.product = p and not exists (
                select other.id from ProductImage other where other.product = p
                and (other.displayOrder < i.displayOrder or (other.displayOrder = i.displayOrder and other.id < i.id)))
            where p.active = true
                and (:search is null or lower(p.name) like :search)
                and (:categoryId is null or c.id = :categoryId)
                and (:filterByCategoryIds = false or c.id in :categoryIds)
                and (:brandId is null or b.id = :brandId)
                and (:minPrice is null or p.price >= :minPrice)
                and (:maxPrice is null or p.price <= :maxPrice)
            """, countQuery = """
            select count(p) from Product p where p.active = true
                and (:search is null or lower(p.name) like :search)
                and (:categoryId is null or p.category.id = :categoryId)
                and (:filterByCategoryIds = false or p.category.id in :categoryIds)
                and (:brandId is null or p.brand.id = :brandId)
                and (:minPrice is null or p.price >= :minPrice)
                and (:maxPrice is null or p.price <= :maxPrice)
            """)
    Page<ProductListItemResponse> findCards(String search, Long categoryId, List<Long> categoryIds,
            boolean filterByCategoryIds, Long brandId, BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable);

    @Query("select p from Product p join fetch p.category join fetch p.brand where p.id = :id")
    Optional<Product> findDetailById(Long id);

    boolean existsByCategory_IdAndActiveTrue(Long categoryId);
    boolean existsByBrand_IdAndActiveTrue(Long brandId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(Long id);

    @Query("select p from Product p join fetch p.category join fetch p.brand where p.id in :ids")
    List<Product> findConfigurationCandidates(Collection<Long> ids);
}
