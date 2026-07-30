package com.computerstore.inventory.repository;
import com.computerstore.inventory.domain.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select inventory from Inventory inventory where inventory.variantId = :variantId")
    Optional<Inventory> findByVariantIdForUpdate(Long variantId);

    @Query("select inventory from Inventory inventory join fetch inventory.variant variant join fetch variant.product product where variant.active and product.active order by product.name, variant.displayOrder, variant.id")
    java.util.List<Inventory> findAllActive();
}
