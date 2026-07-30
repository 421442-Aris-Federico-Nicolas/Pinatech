package com.computerstore.order.controller;

import java.util.List;

import com.computerstore.order.domain.CustomerOrder;
import com.computerstore.order.dto.CheckoutCapabilitiesResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/checkout")
public class CheckoutController {

    private static final CheckoutCapabilitiesResponse NO_PROVIDER_CAPABILITIES =
            new CheckoutCapabilitiesResponse(
                    CustomerOrder.DEFAULT_CURRENCY,
                    true,
                    false,
                    false,
                    List.of(),
                    List.of());

    @GetMapping("/capabilities")
    public CheckoutCapabilitiesResponse capabilities() {
        return NO_PROVIDER_CAPABILITIES;
    }
}
