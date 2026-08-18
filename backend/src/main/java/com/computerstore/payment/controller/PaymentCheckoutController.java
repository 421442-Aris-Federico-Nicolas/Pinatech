package com.computerstore.payment.controller;

import com.computerstore.payment.dto.PaymentCheckoutResponse;
import com.computerstore.payment.service.PaymentCheckoutService;
import com.computerstore.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders/{orderId}/payments")
public class PaymentCheckoutController {

    private final PaymentCheckoutService payments;

    public PaymentCheckoutController(PaymentCheckoutService payments) {
        this.payments = payments;
    }

    @PostMapping("/mercado-pago")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<PaymentCheckoutResponse> create(
            @PathVariable Long orderId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        var result = payments.create(orderId, user.id(), idempotencyKey);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.response());
    }
}
