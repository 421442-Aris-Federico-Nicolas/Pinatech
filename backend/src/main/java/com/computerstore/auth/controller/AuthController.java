package com.computerstore.auth.controller;

import java.time.Duration;

import com.computerstore.auth.dto.AuthResponse;
import com.computerstore.auth.dto.AuthenticatedUserResponse;
import com.computerstore.auth.dto.LoginRequest;
import com.computerstore.auth.dto.RegisterRequest;
import com.computerstore.auth.dto.ActionTokenRequest;
import com.computerstore.auth.dto.EmailRequest;
import com.computerstore.auth.dto.GenericMessageResponse;
import com.computerstore.auth.dto.ResetPasswordRequest;
import com.computerstore.auth.service.AccountLifecycleService;
import com.computerstore.auth.service.AuthService;
import com.computerstore.auth.service.AuthSession;
import com.computerstore.auth.service.AuthRateLimiter;
import com.computerstore.common.exception.AuthenticationFailureException;
import com.computerstore.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String REFRESH_COOKIE = "refresh_token";

    private final AuthService authService;
    private final AuthRateLimiter authRateLimiter;
    private final AccountLifecycleService accountLifecycleService;
    private final long refreshExpirationMs;
    private final boolean secureCookie;

    public AuthController(
            AuthService authService,
            AuthRateLimiter authRateLimiter,
            AccountLifecycleService accountLifecycleService,
            @Value("${app.jwt.refresh-expiration-ms}") long refreshExpirationMs,
            @Value("${app.cookies.secure}") boolean secureCookie
    ) {
        this.authService = authService;
        this.authRateLimiter = authRateLimiter;
        this.accountLifecycleService = accountLifecycleService;
        this.refreshExpirationMs = refreshExpirationMs;
        this.secureCookie = secureCookie;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a customer account")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest servletRequest) {
        authRateLimiter.checkRegistration(servletRequest.getRemoteAddr());
        return withSession(HttpStatus.CREATED, authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate with email and password")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        authRateLimiter.checkLogin(servletRequest.getRemoteAddr(), request.email());
        AuthSession session = authService.login(request);
        authRateLimiter.resetLogin(servletRequest.getRemoteAddr(), request.email());
        return withSession(HttpStatus.OK, session);
    }

    @PostMapping("/email-verification/request")
    @Operation(summary = "Request an email verification message")
    public ResponseEntity<GenericMessageResponse> requestEmailVerification(
            @Valid @RequestBody EmailRequest request,
            HttpServletRequest servletRequest
    ) {
        authRateLimiter.checkAccountAction(servletRequest.getRemoteAddr(), "verify", request.email());
        accountLifecycleService.requestEmailVerification(request.email());
        return genericAccepted();
    }

    @PostMapping("/email-verification/confirm")
    @Operation(summary = "Confirm an email address")
    public ResponseEntity<Void> confirmEmailVerification(
            @Valid @RequestBody ActionTokenRequest request,
            HttpServletRequest servletRequest
    ) {
        authRateLimiter.checkAccountAction(servletRequest.getRemoteAddr(), "verify-confirm", request.token());
        accountLifecycleService.confirmEmailVerification(request.token());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request a password reset message")
    public ResponseEntity<GenericMessageResponse> forgotPassword(
            @Valid @RequestBody EmailRequest request,
            HttpServletRequest servletRequest
    ) {
        authRateLimiter.checkAccountAction(servletRequest.getRemoteAddr(), "forgot", request.email());
        accountLifecycleService.requestPasswordReset(request.email());
        return genericAccepted();
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset an account password")
    public ResponseEntity<Void> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest servletRequest
    ) {
        authRateLimiter.checkAccountAction(servletRequest.getRemoteAddr(), "reset", request.token());
        accountLifecycleService.resetPassword(request.token(), request.password());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate the refresh token and issue a new access token")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(value = REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletRequest servletRequest
    ) {
        authRateLimiter.checkRefresh(servletRequest.getRemoteAddr());
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new AuthenticationFailureException("Refresh token is required.");
        }
        return withSession(HttpStatus.OK, authService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    @Operation(summary = "Invalidate the current refresh token")
    public ResponseEntity<Void> logout(
            @CookieValue(value = REFRESH_COOKIE, required = false) String refreshToken
    ) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            authService.logout(refreshToken);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expiredRefreshCookie().toString())
                .build();
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get the authenticated user")
    public ResponseEntity<AuthenticatedUserResponse> me(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(authService.getCurrentUser(user.id()));
    }

    private ResponseEntity<AuthResponse> withSession(HttpStatus status, AuthSession session) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, refreshCookie(session.refreshToken()).toString())
                .body(session.response());
    }

    private ResponseEntity<GenericMessageResponse> genericAccepted() {
        return ResponseEntity.accepted().body(new GenericMessageResponse(
                "If the account exists, an email with the next steps will be sent."));
    }

    private ResponseCookie refreshCookie(String refreshToken) {
        return ResponseCookie.from(REFRESH_COOKIE, refreshToken)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(Duration.ofMillis(refreshExpirationMs))
                .build();
    }

    private ResponseCookie expiredRefreshCookie() {
        return ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(Duration.ZERO)
                .build();
    }
}
