package com.computerstore.payment.controller;

import com.computerstore.payment.domain.BankTransferProofStatus;
import com.computerstore.payment.dto.*;
import com.computerstore.payment.service.BankTransferService;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import com.computerstore.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/api/admin/bank-transfer-proofs")
@PreAuthorize("hasRole('ADMIN')")
public class AdminBankTransferController {
    private final BankTransferService service;
    public AdminBankTransferController(BankTransferService service) { this.service = service; }

    @GetMapping
    public List<AdminBankTransferProofResponse> list(
            @RequestParam(defaultValue = "PENDING_REVIEW") BankTransferProofStatus status) {
        return service.list(status);
    }

    @GetMapping("/{proofId}/previews/{index}")
    public ResponseEntity<FileSystemResource> preview(@PathVariable UUID proofId, @PathVariable int index) {
        var content = service.preview(proofId, index);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(new FileSystemResource(content.path()));
    }

    @PostMapping("/{proofId}/approve")
    public BankTransferProofResponse approve(@PathVariable UUID proofId,
                                             @Valid @RequestBody ApproveBankTransferRequest request,
                                             @AuthenticationPrincipal AuthenticatedUser admin) {
        return service.approve(proofId, request.amount(), request.reference(), admin);
    }

    @PostMapping("/{proofId}/reject")
    public BankTransferProofResponse reject(@PathVariable UUID proofId,
                                            @Valid @RequestBody RejectBankTransferRequest request,
                                            @AuthenticationPrincipal AuthenticatedUser admin) {
        return service.reject(proofId, request.reason(), admin);
    }
}
