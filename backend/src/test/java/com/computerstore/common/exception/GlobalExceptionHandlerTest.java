package com.computerstore.common.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

class GlobalExceptionHandlerTest {

    @Test
    void identifiesTheGuestAccountConflictForFrontendRecovery() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/guest-checkout/orders");

        var problem = new GlobalExceptionHandler().handleGuestCheckoutAccountRequired(
                new GuestCheckoutAccountRequiredException(), request);

        assertEquals(409, problem.getStatus());
        assertEquals("https://computer-store.dev/errors/guest-checkout-account-required",
                problem.getType().toString());
        assertEquals("Account sign-in required", problem.getTitle());
    }
}
