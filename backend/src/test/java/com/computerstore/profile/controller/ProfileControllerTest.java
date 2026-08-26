package com.computerstore.profile.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import com.computerstore.auth.dto.ActionTokenRequest;
import com.computerstore.auth.service.AuthRateLimiter;
import com.computerstore.profile.service.ProfileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class ProfileControllerTest {

    @Mock private ProfileService profiles;
    @Mock private AuthRateLimiter rateLimiter;

    @Test
    void publicEmailChangeConfirmationIsRateLimitedAndDelegated() {
        ProfileController controller = new ProfileController(profiles, rateLimiter);
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setRemoteAddr("127.0.0.1");

        var response = controller.confirmEmailChange(new ActionTokenRequest("raw-token"), servletRequest);

        assertEquals(204, response.getStatusCode().value());
        verify(rateLimiter).checkAccountAction("127.0.0.1", "email-change-confirm", "raw-token");
        verify(profiles).confirmEmailChange("raw-token");
    }
}
