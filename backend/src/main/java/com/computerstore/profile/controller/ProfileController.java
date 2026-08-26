package com.computerstore.profile.controller;

import com.computerstore.auth.dto.ActionTokenRequest;
import com.computerstore.auth.service.AuthRateLimiter;
import com.computerstore.profile.dto.AddressRequest;
import com.computerstore.profile.dto.AddressResponse;
import com.computerstore.profile.dto.ProfileResponse;
import com.computerstore.profile.dto.UpdateProfileRequest;
import com.computerstore.profile.dto.EmailChangeRequest;
import com.computerstore.profile.service.ProfileService;
import com.computerstore.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
@SecurityRequirement(name = "bearerAuth")
public class ProfileController {

    private final ProfileService profileService;
    private final AuthRateLimiter authRateLimiter;

    public ProfileController(ProfileService profileService, AuthRateLimiter authRateLimiter) {
        this.profileService = profileService;
        this.authRateLimiter = authRateLimiter;
    }

    @GetMapping
    @Operation(summary = "Get the authenticated account profile")
    public ProfileResponse get(@AuthenticationPrincipal AuthenticatedUser user) {
        return profileService.getProfile(user.id());
    }

    @PatchMapping
    @Operation(summary = "Update account profile fields")
    public ProfileResponse update(@AuthenticationPrincipal AuthenticatedUser user,
                                  @Valid @RequestBody UpdateProfileRequest request) {
        return profileService.updateProfile(user.id(), request);
    }

    @PutMapping("/address")
    @Operation(summary = "Create or replace the account address")
    public AddressResponse putAddress(@AuthenticationPrincipal AuthenticatedUser user,
                                      @Valid @RequestBody AddressRequest request) {
        return profileService.putAddress(user.id(), request);
    }

    @DeleteMapping("/address")
    @Operation(summary = "Delete the account address")
    public ResponseEntity<Void> deleteAddress(@AuthenticationPrincipal AuthenticatedUser user) {
        profileService.deleteAddress(user.id());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/email-change/request")
    @Operation(summary = "Request confirmation of a new account email")
    public ResponseEntity<Void> requestEmailChange(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody EmailChangeRequest request,
            HttpServletRequest servletRequest
    ) {
        authRateLimiter.checkAccountAction(servletRequest.getRemoteAddr(), "email-change", user.id().toString());
        profileService.requestEmailChange(user.id(), request.email(), request.currentPassword());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/email-change/confirm")
    @Operation(summary = "Confirm a new account email")
    public ResponseEntity<Void> confirmEmailChange(
            @Valid @RequestBody ActionTokenRequest request,
            HttpServletRequest servletRequest
    ) {
        authRateLimiter.checkAccountAction(servletRequest.getRemoteAddr(), "email-change-confirm", request.token());
        profileService.confirmEmailChange(request.token());
        return ResponseEntity.noContent().build();
    }
}
