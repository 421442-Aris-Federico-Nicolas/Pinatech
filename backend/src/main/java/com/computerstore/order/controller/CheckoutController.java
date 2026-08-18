package com.computerstore.order.controller;

import java.util.List;

import com.computerstore.order.domain.CustomerOrder;
import com.computerstore.order.dto.CheckoutCapabilitiesResponse;
import com.computerstore.payment.config.MercadoPagoProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/checkout")
public class CheckoutController {

    private final MercadoPagoProperties mercadoPago;

    public CheckoutController(MercadoPagoProperties mercadoPago) {
        this.mercadoPago = mercadoPago;
    }

    @GetMapping("/capabilities")
    public CheckoutCapabilitiesResponse capabilities() {
        return new CheckoutCapabilitiesResponse(
                CustomerOrder.DEFAULT_CURRENCY,
                true,
                mercadoPago.enabled(),
                false,
                mercadoPago.enabled() ? List.of("MERCADO_PAGO") : List.of(),
                List.of());
    }
}
