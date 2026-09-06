package com.computerstore.order.controller;

import com.computerstore.order.dto.ConfirmBankTransferRefundRequest;
import com.computerstore.order.dto.OrderResponse;
import com.computerstore.security.AuthenticatedUser;
import com.computerstore.shipping.service.ShipmentDispatchService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminOrderCancellationController {
    private final ShipmentDispatchService shipments;

    public AdminOrderCancellationController(ShipmentDispatchService shipments) {
        this.shipments = shipments;
    }

    @PostMapping("/api/admin/orders/{orderId}/bank-transfer-refund/confirm")
    @PreAuthorize("hasRole('ADMIN')")
    public OrderResponse confirmBankTransferRefund(@PathVariable Long orderId,
            @Valid @RequestBody ConfirmBankTransferRefundRequest request,
            @AuthenticationPrincipal AuthenticatedUser auth) {
        return shipments.confirmBankTransferRefund(orderId, request.reference(), auth.id());
    }
}
