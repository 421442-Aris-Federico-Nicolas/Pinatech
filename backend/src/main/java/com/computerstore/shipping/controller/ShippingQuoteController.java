package com.computerstore.shipping.controller;

import com.computerstore.security.AuthenticatedUser;
import com.computerstore.shipping.dto.*;
import com.computerstore.shipping.service.ShippingQuoteService;
import com.computerstore.shipping.service.ShippingQuoteRateLimiter;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/shipping/quotes")
public class ShippingQuoteController {
    private final ShippingQuoteService service;
    private final ShippingQuoteRateLimiter rateLimiter;
    public ShippingQuoteController(ShippingQuoteService service, ShippingQuoteRateLimiter rateLimiter) {
        this.service = service;
        this.rateLimiter = rateLimiter;
    }
    @PostMapping @PreAuthorize("hasRole('CUSTOMER')")
    public ShippingQuoteResponse quote(@Valid @RequestBody ShippingQuoteRequest request,
                                       @AuthenticationPrincipal AuthenticatedUser user) {
        rateLimiter.check(user.id());
        return service.quote(user.id(), request);
    }
}
