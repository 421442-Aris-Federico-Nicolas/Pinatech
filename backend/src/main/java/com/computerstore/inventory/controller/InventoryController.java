package com.computerstore.inventory.controller;

import com.computerstore.common.exception.ResourceNotFoundException;
import com.computerstore.inventory.domain.InventoryMovement;
import com.computerstore.inventory.dto.InventoryAdjustmentRequest;
import com.computerstore.inventory.dto.InventoryResponse;
import com.computerstore.inventory.repository.InventoryMovementRepository;
import com.computerstore.inventory.repository.InventoryRepository;
import com.computerstore.security.AuthenticatedUser;
import com.computerstore.user.repository.UserAccountRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {
    private final InventoryRepository repository;
    private final InventoryMovementRepository movementRepository;
    private final UserAccountRepository userRepository;

    public InventoryController(
            InventoryRepository repository,
            InventoryMovementRepository movementRepository,
            UserAccountRepository userRepository
    ) {
        this.repository = repository;
        this.movementRepository = movementRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/{productId}")
    @PreAuthorize("hasAnyRole('ADMIN','TECHNICIAN')")
    public InventoryResponse get(@PathVariable Long productId) {
        return toResponse(repository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found.")));
    }

    @PostMapping("/adjustments")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<InventoryResponse> adjust(
            @Valid @RequestBody InventoryAdjustmentRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        var inventory = repository.findById(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found."));
        inventory.adjust(request.quantity());
        var actor = userRepository.findById(user.id())
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
        movementRepository.save(new InventoryMovement(
                inventory.getProduct(), request.quantity(), request.reason().trim(), actor));
        return ResponseEntity.ok(toResponse(inventory));
    }

    private InventoryResponse toResponse(com.computerstore.inventory.domain.Inventory inventory) {
        return new InventoryResponse(
                inventory.getProductId(), inventory.getAvailableQuantity(), inventory.getReservedQuantity());
    }
}
