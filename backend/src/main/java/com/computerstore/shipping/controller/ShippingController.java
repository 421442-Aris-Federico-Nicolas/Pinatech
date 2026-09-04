package com.computerstore.shipping.controller;

import java.util.List;
import com.computerstore.common.exception.*;
import com.computerstore.order.repository.CustomerOrderRepository;
import com.computerstore.security.AuthenticatedUser;
import com.computerstore.shipping.domain.OrderShipment;
import com.computerstore.shipping.dto.ShipmentResponse;
import com.computerstore.shipping.gateway.ZipnovaGateway;
import com.computerstore.shipping.repository.*;
import com.computerstore.shipping.service.ShipmentDispatchService;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
public class ShippingController {
    private final OrderShipmentRepository shipments; private final ShipmentEventRepository events;
    private final CustomerOrderRepository orders; private final ShipmentDispatchService dispatch; private final ZipnovaGateway gateway;
    public ShippingController(OrderShipmentRepository shipments, ShipmentEventRepository events,
            CustomerOrderRepository orders, ShipmentDispatchService dispatch, ZipnovaGateway gateway) {
        this.shipments = shipments; this.events = events; this.orders = orders; this.dispatch = dispatch; this.gateway = gateway;
    }
    @GetMapping("/api/shipping/orders/{orderId}/tracking") @PreAuthorize("hasRole('CUSTOMER')") @Transactional(readOnly = true)
    public ShipmentResponse tracking(@PathVariable Long orderId, @AuthenticationPrincipal AuthenticatedUser auth) {
        var order = orders.findById(orderId).filter(value -> value.getUser().getId().equals(auth.id()))
                .orElseThrow(() -> new ResourceNotFoundException("Order not found."));
        return response(shipments.findByOrderId(order.getId()).orElseThrow(() -> new ResourceNotFoundException("Shipment not found.")));
    }
    @PostMapping("/api/admin/shipping/orders/{orderId}/retry") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> retry(@PathVariable Long orderId) { dispatch.retry(orderId); return ResponseEntity.accepted().build(); }
    @GetMapping("/api/admin/shipping/orders/{orderId}/label") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> label(@PathVariable Long orderId) { return pdf(orderId, true); }
    @GetMapping("/api/admin/shipping/orders/{orderId}/document") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> document(@PathVariable Long orderId) { return pdf(orderId, false); }
    @PostMapping("/api/admin/shipping/orders/{orderId}/cancel") @PreAuthorize("hasRole('ADMIN')") @Transactional
    public java.util.Map<String,String> cancel(@PathVariable Long orderId) {
        OrderShipment shipment = providerShipment(orderId); String result = gateway.cancel(shipment.getProviderShipmentId());
        shipment.cancelled(java.time.Instant.now()); return java.util.Map.of("result", result);
    }
    private ResponseEntity<byte[]> pdf(Long orderId, boolean label) {
        OrderShipment shipment = providerShipment(orderId);
        byte[] body = label ? gateway.label(shipment.getProviderShipmentId()) : gateway.document(shipment.getProviderShipmentId());
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"PIN-" + orderId + (label ? "-label.pdf\"" : "-document.pdf\""))
                .header("X-Content-Type-Options", "nosniff").cacheControl(CacheControl.noStore()).body(body);
    }
    private OrderShipment providerShipment(Long orderId) { return shipments.findByOrderId(orderId)
            .filter(value -> value.getProviderShipmentId() != null)
            .orElseThrow(() -> new ResourceNotFoundException("Shipment not found.")); }
    private ShipmentResponse response(OrderShipment shipment) {
        List<ShipmentResponse.Event> history = events.findByShipmentIdOrderByOccurredAtAscIdAsc(shipment.getId()).stream()
                .map(event -> new ShipmentResponse.Event(event.getRawStatus(), event.getRawSubstatus(), event.getOccurredAt())).toList();
        var order = shipment.getOrder();
        return new ShipmentResponse(shipment.getStatus().name(), shipment.getRawStatus(), shipment.getRawSubstatus(),
                order.getShippingCarrierName(), shipment.getCarrierTrackingId(), shipment.getTrackingUrl(),
                shipment.getEstimatedDeliveryAt() == null ? order.getShippingEta() : shipment.getEstimatedDeliveryAt(),
                shipment.isIncident(), history);
    }
}
