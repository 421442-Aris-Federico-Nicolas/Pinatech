package com.computerstore.inventory.repository;
import com.computerstore.inventory.domain.InventoryMovement;
import org.springframework.data.jpa.repository.JpaRepository;
public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {}
