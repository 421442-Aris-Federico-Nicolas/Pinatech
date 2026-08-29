package com.computerstore.profile.service;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

import com.computerstore.auth.repository.RefreshTokenRepository;
import com.computerstore.common.exception.DuplicateResourceException;
import com.computerstore.common.exception.InvalidRequestException;
import com.computerstore.common.exception.ResourceNotFoundException;
import com.computerstore.common.exception.AuthenticationFailureException;
import com.computerstore.email.TransactionalEmailService;
import com.computerstore.profile.dto.AddressRequest;
import com.computerstore.profile.dto.AddressResponse;
import com.computerstore.profile.dto.ProfileResponse;
import com.computerstore.profile.dto.UpdateProfileRequest;
import com.computerstore.user.domain.AccountActionPurpose;
import com.computerstore.user.domain.AccountActionToken;
import com.computerstore.user.domain.UserAccount;
import com.computerstore.user.domain.UserAddress;
import com.computerstore.user.repository.UserAccountRepository;
import com.computerstore.user.repository.UserAddressRepository;
import com.computerstore.user.service.AccountActionTokenService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

@Service
public class ProfileService {

    private final UserAccountRepository userRepository;
    private final UserAddressRepository addressRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AccountActionTokenService tokenService;
    private final TransactionalEmailService emailService;
    private final PasswordEncoder passwordEncoder;

    public ProfileService(
            UserAccountRepository userRepository,
            UserAddressRepository addressRepository,
            RefreshTokenRepository refreshTokenRepository,
            AccountActionTokenService tokenService,
            TransactionalEmailService emailService,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenService = tokenService;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(Long userId) {
        return toResponse(activeUser(userId));
    }

    @Transactional
    public ProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        if (request.firstName() == null && request.lastName() == null && request.phone() == null) {
            throw new InvalidRequestException("At least one profile field is required.");
        }
        UserAccount user = activeUser(userId);
        user.updateProfile(trim(request.firstName()), trim(request.lastName()), trimAllowEmpty(request.phone()));
        return toResponse(user);
    }

    @Transactional
    public AddressResponse putAddress(Long userId, AddressRequest request) {
        UserAccount user = activeUser(userId);
        UserAddress address = addressRepository.findById(userId).orElseGet(() -> new UserAddress(user));
        address.update(
                request.street().trim(),
                request.number().trim(),
                optional(request.floorApartment()),
                request.locality().trim(),
                request.provinceCode().trim().toUpperCase(),
                request.postalCode().trim().toUpperCase(),
                request.countryCode() == null ? "AR" : request.countryCode().trim().toUpperCase(),
                optional(request.reference()));
        return toAddress(addressRepository.save(address));
    }

    @Transactional
    public void deleteAddress(Long userId) {
        activeUser(userId);
        addressRepository.deleteById(userId);
    }

    @Transactional
    public void requestEmailChange(Long userId, String requestedEmail, String currentPassword) {
        UserAccount user = userRepository.findByIdForUpdate(userId)
                .filter(UserAccount::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
        requireCurrentPassword(user, currentPassword);
        String targetEmail = requestedEmail.trim().toLowerCase();
        if (user.getEmail().equalsIgnoreCase(targetEmail)) {
            throw new InvalidRequestException("The new email must differ from the current email.");
        }
        if (userRepository.existsByEmailIgnoreCase(targetEmail)) {
            throw new DuplicateResourceException("The requested email is not available.");
        }
        String token = tokenService.issue(user, AccountActionPurpose.EMAIL_CHANGE, targetEmail);
        emailService.sendAccountAction(targetEmail, user.getFirstName(), AccountActionPurpose.EMAIL_CHANGE, token);
    }

    @Transactional
    public void confirmEmailChange(String rawToken) {
        AccountActionToken actionToken = tokenService.consume(rawToken, AccountActionPurpose.EMAIL_CHANGE);
        UserAccount user = userRepository.findByIdForUpdate(actionToken.getUser().getId())
                .filter(UserAccount::isActive)
                .orElseThrow(() -> new InvalidRequestException("The account action token is invalid or expired."));
        String targetEmail = actionToken.getTargetEmail();
        userRepository.findByEmailIgnoreCase(targetEmail)
                .filter(existing -> !existing.getId().equals(user.getId()))
                .ifPresent(existing -> {
                    throw new DuplicateResourceException("The requested email is not available.");
                });
        String previousEmail = user.getEmail();
        user.changeEmail(targetEmail);
        user.markEmailVerified();
        user.incrementSessionVersion();
        try {
            userRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateResourceException("The requested email is not available.");
        }
        refreshTokenRepository.revokeAllByUserId(user.getId(), Instant.now());
        tokenService.invalidateAll(user);
        emailService.sendEmailChangedNotice(previousEmail, user.getFirstName(), targetEmail);
    }

    private UserAccount activeUser(Long userId) {
        return userRepository.findByIdAndActiveTrue(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
    }

    private void requireCurrentPassword(UserAccount user, String currentPassword) {
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new AuthenticationFailureException("Authentication failed.");
        }
    }

    private ProfileResponse toResponse(UserAccount user) {
        Set<String> roles = user.getRoles().stream()
                .map(role -> role.getName().name())
                .collect(Collectors.toUnmodifiableSet());
        AddressResponse address = addressRepository.findById(user.getId()).map(this::toAddress).orElse(null);
        return new ProfileResponse(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(),
                user.getPhone(), user.isEmailVerified(), roles, address);
    }

    private AddressResponse toAddress(UserAddress address) {
        return new AddressResponse(address.getStreet(), address.getStreetNumber(), address.getFloorApartment(),
                address.getLocality(), address.getProvinceCode(), address.getPostalCode(),
                address.getCountryCode(), address.getReference());
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String trimAllowEmpty(String value) {
        return value == null ? null : value.trim();
    }

    private String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
