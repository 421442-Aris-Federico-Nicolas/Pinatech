package com.computerstore.inventory.repository;
import com.computerstore.inventory.domain.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface InventoryRepository extends JpaRepository<Inventory, Long> { @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select inventory from Inventory inventory where inventory.productId = :productId") Optional<Inventory> findByProductIdForUpdate(Long productId); }
