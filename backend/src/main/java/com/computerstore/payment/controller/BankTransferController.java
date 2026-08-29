package com.computerstore.payment.controller;

import com.computerstore.payment.dto.BankTransferDetailResponse;
import com.computerstore.payment.service.BankTransferService;
import com.computerstore.payment.service.BankTransferProofSanitizer;
import com.computerstore.security.AuthenticatedUser;
import com.computerstore.auth.service.AuthRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/orders/{orderId}/bank-transfer")
@PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
public class BankTransferController {
    private final BankTransferService service;
    private final BankTransferProofSanitizer sanitizer;
    private final AuthRateLimiter rateLimiter;
    public BankTransferController(BankTransferService service, BankTransferProofSanitizer sanitizer,
                                  AuthRateLimiter rateLimiter) {
        this.service = service;
        this.sanitizer = sanitizer;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping
    public BankTransferDetailResponse detail(@PathVariable Long orderId,
                                             @AuthenticationPrincipal AuthenticatedUser auth) {
        return service.detail(orderId, auth);
    }

    @PostMapping(value = "/proof", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CUSTOMER')")
    public BankTransferDetailResponse upload(@PathVariable Long orderId, @RequestPart("file") MultipartFile file,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal AuthenticatedUser auth, HttpServletRequest servletRequest) {
        rateLimiter.checkAccountAction(servletRequest.getRemoteAddr(), "bank-proof-upload-ip", "all");
        rateLimiter.checkAccountAction(servletRequest.getRemoteAddr(), "bank-proof-upload-account", auth.id().toString());
        BankTransferDetailResponse existing = service.preflightUpload(orderId, idempotencyKey, auth);
        if (existing != null) return existing;
        return service.upload(orderId, sanitizer.sanitize(file), idempotencyKey, auth);
    }
}
