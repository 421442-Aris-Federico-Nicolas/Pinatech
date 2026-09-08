package com.computerstore.inventory.repository;
import com.computerstore.inventory.domain.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.computerstore.inventory.dto.InventorySummaryResponse;
public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select inventory from Inventory inventory where inventory.variantId = :variantId")
    Optional<Inventory> findByVariantIdForUpdate(Long variantId);

    @EntityGraph(attributePaths = {"variant.product.id"})
    @Query("select inventory from Inventory inventory join inventory.variant variant join variant.product product where variant.active and product.active order by product.name, variant.displayOrder, variant.id")
    List<Inventory> findAllActive();

    @EntityGraph(attributePaths = {"variant.product.brand.name"})
    @Query("""
            select i from Inventory i join i.variant v join v.product p left join p.brand b
            where v.active = true and p.active = true
              and (:search = '' or lower(p.name) like lower(concat('%', :search, '%'))
                   or lower(b.name) like lower(concat('%', :search, '%'))
                   or lower(v.colorName) like lower(concat('%', :search, '%')))
            order by p.name, v.displayOrder, v.id
            """)
    Page<Inventory> findActivePage(@Param("search") String search, Pageable pageable);

    @Query("""
            select new com.computerstore.inventory.dto.InventorySummaryResponse(
                coalesce(sum(case when i.availableQuantity <= 5 then 1L else 0L end), 0L),
                coalesce(sum(i.availableQuantity), 0L))
            from Inventory i join i.variant v join v.product p where v.active = true and p.active = true
            """)
    InventorySummaryResponse summarizeActive();
}
