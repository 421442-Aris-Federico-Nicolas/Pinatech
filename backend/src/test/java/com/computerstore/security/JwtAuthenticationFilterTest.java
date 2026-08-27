package com.computerstore.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock private JwtService jwtService;
    @Mock private CustomUserDetailsService users;
    @Mock private FilterChain chain;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsAnAccessTokenFromAnOlderSessionVersion() throws Exception {
        MockHttpServletRequest request = bearerRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtService.extractSession("access-token")).thenReturn(new JwtService.JwtSession(7L, 2));
        when(users.loadActiveUserById(7L)).thenReturn(
                new AuthenticatedUser(7L, "user@example.com", 3, List.of()));

        new JwtAuthenticationFilter(jwtService, users).doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
    }

    @Test
    void authenticatesAnAccessTokenForTheCurrentSessionVersion() throws Exception {
        MockHttpServletRequest request = bearerRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtService.extractSession("access-token")).thenReturn(new JwtService.JwtSession(7L, 3));
        when(users.loadActiveUserById(7L)).thenReturn(
                new AuthenticatedUser(7L, "user@example.com", 3, List.of()));

        new JwtAuthenticationFilter(jwtService, users).doFilter(request, response, chain);

        assertEquals(7L, ((AuthenticatedUser) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal()).id());
        verify(chain).doFilter(request, response);
    }

    private MockHttpServletRequest bearerRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer access-token");
        return request;
    }
}
