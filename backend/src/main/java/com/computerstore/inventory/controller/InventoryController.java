package com.computerstore.inventory.controller;

import com.computerstore.common.exception.ResourceNotFoundException;
import com.computerstore.catalog.repository.ProductVariantRepository;
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
import java.util.List;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {
    private final InventoryRepository repository;
    private final ProductVariantRepository variants;
    private final InventoryMovementRepository movementRepository;
    private final UserAccountRepository userRepository;

    public InventoryController(
            InventoryRepository repository,
            ProductVariantRepository variants,
            InventoryMovementRepository movementRepository,
            UserAccountRepository userRepository
    ) {
        this.repository = repository;
        this.variants = variants;
        this.movementRepository = movementRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/{variantId}")
    @PreAuthorize("hasAnyRole('ADMIN','TECHNICIAN')")
    @Transactional(readOnly = true)
    public InventoryResponse get(@PathVariable Long variantId) {
        return toResponse(repository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found.")));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','TECHNICIAN')")
    @Transactional(readOnly = true)
    public List<InventoryResponse> list() {
        return repository.findAllActive().stream().map(this::toResponse).toList();
    }

    @PostMapping("/adjustments")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<InventoryResponse> adjust(
            @Valid @RequestBody InventoryAdjustmentRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        variants.findByIdAndActiveTrueAndProduct_ActiveTrue(request.variantId())
                .orElseThrow(() -> new ResourceNotFoundException("Product variant not found."));
        var inventory = repository.findByVariantIdForUpdate(request.variantId())
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found."));
        inventory.adjust(request.quantity());
        var actor = userRepository.findById(user.id())
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
        movementRepository.save(new InventoryMovement(
                inventory.getVariant(), request.quantity(), request.reason().trim(), actor));
        return ResponseEntity.ok(toResponse(inventory));
    }

    private InventoryResponse toResponse(com.computerstore.inventory.domain.Inventory inventory) {
        return new InventoryResponse(
                inventory.getVariant().getProduct().getId(), inventory.getVariantId(), inventory.getVariant().getColorName(),
                inventory.getVariant().getColorHex(), inventory.getAvailableQuantity(), inventory.getReservedQuantity());
    }
}
