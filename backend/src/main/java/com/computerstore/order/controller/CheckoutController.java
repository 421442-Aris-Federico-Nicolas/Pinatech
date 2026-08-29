package com.computerstore.order.controller;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.computerstore.order.domain.CustomerOrder;
import com.computerstore.order.dto.CheckoutCapabilitiesResponse;
import com.computerstore.order.dto.PickupLocationResponse;
import com.computerstore.order.service.FulfillmentPolicy;
import com.computerstore.payment.config.BankTransferProperties;
import com.computerstore.payment.config.MercadoPagoProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/checkout")
public class CheckoutController {

    private final MercadoPagoProperties mercadoPago;
    private final FulfillmentPolicy fulfillment;
    private final BankTransferProperties bankTransfer;

    @Autowired
    public CheckoutController(MercadoPagoProperties mercadoPago, FulfillmentPolicy fulfillment,
                              ObjectProvider<BankTransferProperties> bankTransfer) {
        this.mercadoPago = mercadoPago;
        this.fulfillment = fulfillment;
        this.bankTransfer = bankTransfer.getIfAvailable(() ->
                new BankTransferProperties(false, "", "", "", "", "", "ARS", null));
    }

    public CheckoutController(MercadoPagoProperties mercadoPago, FulfillmentPolicy fulfillment) {
        this.mercadoPago = mercadoPago;
        this.fulfillment = fulfillment;
        this.bankTransfer = new BankTransferProperties(false, "", "", "", "", "", "ARS", null);
    }

    @GetMapping("/capabilities")
    public CheckoutCapabilitiesResponse capabilities() {
        List<String> paymentMethods = new ArrayList<>();
        if (mercadoPago.enabled()) paymentMethods.add("MERCADO_PAGO");
        if (bankTransfer.available()) paymentMethods.add("BANK_TRANSFER");
        return new CheckoutCapabilitiesResponse(
                CustomerOrder.DEFAULT_CURRENCY,
                !fulfillment.availableMethods().isEmpty(),
                mercadoPago.enabled(),
                false,
                BigDecimal.ZERO,
                OrderController.BANK_TRANSFER_DISCOUNT_RATE,
                paymentMethods,
                List.of(),
                fulfillment.availableMethods().stream().map(Enum::name).toList(),
                fulfillment.activePickupLocation().stream().map(PickupLocationResponse::from).toList());
    }
}
