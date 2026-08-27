package com.computerstore.order.controller;

import java.util.List;

import com.computerstore.order.domain.CustomerOrder;
import com.computerstore.order.dto.CheckoutCapabilitiesResponse;
import com.computerstore.order.dto.PickupLocationResponse;
import com.computerstore.order.service.FulfillmentPolicy;
import com.computerstore.payment.config.MercadoPagoProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/checkout")
public class CheckoutController {

    private final MercadoPagoProperties mercadoPago;
    private final FulfillmentPolicy fulfillment;

    public CheckoutController(MercadoPagoProperties mercadoPago, FulfillmentPolicy fulfillment) {
        this.mercadoPago = mercadoPago;
        this.fulfillment = fulfillment;
    }

    @GetMapping("/capabilities")
    public CheckoutCapabilitiesResponse capabilities() {
        return new CheckoutCapabilitiesResponse(
                CustomerOrder.DEFAULT_CURRENCY,
                !fulfillment.availableMethods().isEmpty(),
                mercadoPago.enabled(),
                false,
                mercadoPago.enabled() ? List.of("MERCADO_PAGO") : List.of(),
                List.of(),
                fulfillment.availableMethods().stream().map(Enum::name).toList(),
                fulfillment.activePickupLocation().stream().map(PickupLocationResponse::from).toList());
    }
}
