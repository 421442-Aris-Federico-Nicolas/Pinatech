package com.computerstore.guest.controller;

import com.computerstore.guest.dto.*;
import com.computerstore.guest.service.*;
import com.computerstore.shipping.dto.ShippingQuoteResponse;
import com.computerstore.shipping.service.ShippingQuoteRateLimiter;
import com.computerstore.shipping.service.ShippingQuoteService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/guest-checkout")
public class GuestCheckoutController {
    private final GuestSessionService sessions;
    private final GuestRequestSecurity security;
    private final ShippingQuoteService shipping;
    private final ShippingQuoteRateLimiter quoteRateLimiter;
    private final GuestOrderService orders;
    private final GuestCheckoutEligibilityService eligibility;

    public GuestCheckoutController(GuestSessionService sessions, GuestRequestSecurity security,
            ShippingQuoteService shipping, ShippingQuoteRateLimiter quoteRateLimiter, GuestOrderService orders,
            GuestCheckoutEligibilityService eligibility) {
        this.sessions = sessions; this.security = security; this.shipping = shipping;
        this.quoteRateLimiter = quoteRateLimiter; this.orders = orders; this.eligibility = eligibility;
    }

    @PostMapping("/sessions")
    public ResponseEntity<GuestSessionResponse> createSession(HttpServletRequest request) {
        var created = sessions.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.SET_COOKIE, created.cookie().toString())
                .cacheControl(CacheControl.noStore()).body(created.response());
    }

    @GetMapping("/session")
    public ResponseEntity<GuestSessionResponse> session(HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(sessions.refreshCsrf(request));
    }

    @PostMapping("/email-eligibility")
    public ResponseEntity<Void> emailEligibility(
            @Valid @RequestBody GuestEmailEligibilityRequest body, HttpServletRequest request) {
        eligibility.check(body.email(), request);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/email-verification/request")
    public ResponseEntity<GuestEmailVerificationRequest.Accepted> requestVerification(
            @Valid @RequestBody GuestEmailVerificationRequest.Start body, HttpServletRequest request) {
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore())
                .body(sessions.requestVerification(body, request));
    }

    @PostMapping("/email-verification/confirm")
    public ResponseEntity<GuestSessionResponse> confirmVerification(
            @Valid @RequestBody GuestEmailVerificationRequest.Confirm body, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(sessions.confirm(body, request));
    }

    @PostMapping("/shipping-quotes")
    public ShippingQuoteResponse quote(@Valid @RequestBody GuestShippingQuoteRequest body,
                                       HttpServletRequest request) {
        security.validateOrigin(request);
        var session = security.session(request, true);
        quoteRateLimiter.check(session.getId());
        quoteRateLimiter.check("guest-ip:" + request.getRemoteAddr());
        return shipping.quoteGuest(session, body);
    }

    @PostMapping("/orders")
    public ResponseEntity<GuestOrderResponse> createOrder(@Valid @RequestBody GuestCreateOrderRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey, HttpServletRequest request) {
        var result = orders.create(body, idempotencyKey, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .cacheControl(CacheControl.noStore()).body(result.response());
    }
}
