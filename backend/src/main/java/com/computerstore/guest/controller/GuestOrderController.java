package com.computerstore.guest.controller;

import com.computerstore.auth.service.AuthRateLimiter;
import com.computerstore.common.exception.ResourceNotFoundException;
import com.computerstore.guest.dto.GuestOrderResponse;
import com.computerstore.guest.service.*;
import com.computerstore.payment.dto.*;
import com.computerstore.payment.service.*;
import com.computerstore.security.AuthenticatedUser;
import com.computerstore.shipping.dto.ShipmentResponse;
import com.computerstore.shipping.repository.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/guest-orders/{publicId}")
public class GuestOrderController {
    private final GuestOrderService orders;
    private final GuestOrderAccessService access;
    private final PaymentCheckoutService payments;
    private final BankTransferService transfers;
    private final BankTransferProofSanitizer sanitizer;
    private final AuthRateLimiter rateLimiter;
    private final OrderShipmentRepository shipments;
    private final ShipmentEventRepository events;

    public GuestOrderController(GuestOrderService orders, GuestOrderAccessService access,
            PaymentCheckoutService payments, BankTransferService transfers, BankTransferProofSanitizer sanitizer,
            AuthRateLimiter rateLimiter, OrderShipmentRepository shipments, ShipmentEventRepository events) {
        this.orders = orders; this.access = access; this.payments = payments; this.transfers = transfers;
        this.sanitizer = sanitizer; this.rateLimiter = rateLimiter; this.shipments = shipments; this.events = events;
    }

    @GetMapping
    public ResponseEntity<GuestOrderResponse> order(@PathVariable UUID publicId, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(orders.get(publicId, request));
    }

    @PostMapping("/payments/mercado-pago")
    public ResponseEntity<PaymentCheckoutResponse> mercadoPago(@PathVariable UUID publicId,
            @RequestHeader("Idempotency-Key") String idempotencyKey, HttpServletRequest request) {
        var order = access.require(publicId, request, true);
        var result = payments.createGuest(order.getId(), idempotencyKey);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .cacheControl(CacheControl.noStore()).body(result.response());
    }

    @GetMapping("/bank-transfer")
    public ResponseEntity<BankTransferDetailResponse> transfer(@PathVariable UUID publicId,
                                                                HttpServletRequest request) {
        var order = access.require(publicId, request, false);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(transfers.detailGuest(order.getId()));
    }

    @PostMapping(value = "/bank-transfer/proof", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BankTransferDetailResponse> proof(@PathVariable UUID publicId,
            @RequestPart("file") MultipartFile file, @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest request) {
        var order = access.require(publicId, request, true);
        rateLimiter.checkAccountAction(request.getRemoteAddr(), "guest-bank-proof-ip", publicId.toString());
        BankTransferDetailResponse existing = transfers.preflightUploadGuest(order.getId(), idempotencyKey);
        if (existing != null) return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(existing);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(transfers.uploadGuest(order.getId(), sanitizer.sanitize(file), idempotencyKey));
    }

    @GetMapping("/tracking")
    @Transactional(readOnly = true)
    public ResponseEntity<ShipmentResponse> tracking(@PathVariable UUID publicId, HttpServletRequest request) {
        var order = access.require(publicId, request, false);
        var shipment = shipments.findByOrderId(order.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Shipment not found."));
        List<ShipmentResponse.Event> history = shipment.getProviderShipmentId() == null ? List.of() : events
                .findByShipmentIdAndProviderShipmentIdOrderByOccurredAtAscIdAsc(shipment.getId(), shipment.getProviderShipmentId())
                .stream().map(event -> new ShipmentResponse.Event(event.getRawStatus(), event.getRawSubstatus(),
                        event.getOccurredAt())).toList();
        var response = new ShipmentResponse(shipment.getStatus().name(), shipment.getRawStatus(),
                shipment.getRawSubstatus(), order.getShippingCarrierName(), shipment.getCarrierTrackingId(),
                shipment.getTrackingUrl(), shipment.getEstimatedDeliveryAt() == null ? order.getShippingEta()
                : shipment.getEstimatedDeliveryAt(), shipment.isIncident(), history);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }

    @PostMapping("/claim")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<GuestOrderResponse> claim(@PathVariable UUID publicId,
            @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(orders.claim(publicId, user.id(), request));
    }
}
