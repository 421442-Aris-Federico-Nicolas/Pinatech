package com.computerstore.auth.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;

import com.computerstore.auth.domain.RefreshToken;
import com.computerstore.auth.dto.AuthResponse;
import com.computerstore.auth.dto.AuthenticatedUserResponse;
import com.computerstore.auth.dto.LoginRequest;
import com.computerstore.auth.dto.RegisterRequest;
import com.computerstore.auth.repository.RefreshTokenRepository;
import com.computerstore.common.exception.BusinessRuleException;
import com.computerstore.common.exception.AuthenticationFailureException;
import com.computerstore.common.exception.DuplicateResourceException;
import com.computerstore.common.exception.ResourceNotFoundException;
import com.computerstore.security.AuthenticatedUser;
import com.computerstore.security.JwtService;
import com.computerstore.user.domain.Role;
import com.computerstore.user.domain.RoleName;
import com.computerstore.user.domain.UserAccount;
import com.computerstore.user.repository.RoleRepository;
import com.computerstore.user.repository.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserAccountRepository userAccountRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final long refreshExpirationMs;

    public AuthService(
            UserAccountRepository userAccountRepository,
            RoleRepository roleRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            @Value("${app.jwt.refresh-expiration-ms}") long refreshExpirationMs
    ) {
        this.userAccountRepository = userAccountRepository;
        this.roleRepository = roleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    @Transactional
    public AuthSession register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userAccountRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new DuplicateResourceException("An account already exists for this email.");
        }

        Role customerRole = roleRepository.findByName(RoleName.CUSTOMER)
                .orElseThrow(() -> new IllegalStateException("Customer role is not configured."));
        UserAccount user = new UserAccount(
                request.firstName().trim(),
                request.lastName().trim(),
                email,
                passwordEncoder.encode(request.password()),
                normalizeOptional(request.phone())
        );
        user.addRole(customerRole);
        UserAccount savedUser = userAccountRepository.save(user);
        LOGGER.info("Registered user with id={}", savedUser.getId());
        return createSession(savedUser);
    }

    @Transactional
    public AuthSession login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        UserAccount user = userAccountRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> invalidCredentials(email));
        if (!user.isActive() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials(email);
        }
        return createSession(user);
    }

    @Transactional
    public AuthSession refresh(String rawRefreshToken) {
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
                .orElseThrow(() -> new AuthenticationFailureException("Refresh token is invalid or expired."));
        if (!storedToken.isUsable(Instant.now())) {
            storedToken.revoke();
            throw new AuthenticationFailureException("Refresh token is invalid or expired.");
        }
        storedToken.revoke();
        return createSession(storedToken.getUser());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenRepository.findByTokenHash(hash(rawRefreshToken)).ifPresent(RefreshToken::revoke);
    }

    @Transactional(readOnly = true)
    public AuthenticatedUserResponse getCurrentUser(Long userId) {
        UserAccount user = userAccountRepository.findByIdAndActiveTrue(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
        return toResponse(user);
    }

    private AuthSession createSession(UserAccount user) {
        AuthenticatedUser principal = AuthenticatedUser.from(user);
        String rawRefreshToken = generateRefreshToken();
        refreshTokenRepository.save(new RefreshToken(
                user,
                hash(rawRefreshToken),
                Instant.now().plusMillis(refreshExpirationMs)
        ));
        AuthResponse response = new AuthResponse(
                jwtService.generateAccessToken(principal),
                "Bearer",
                jwtService.getAccessExpirationSeconds(),
                toResponse(user)
        );
        return new AuthSession(response, rawRefreshToken);
    }

    private AuthenticationFailureException invalidCredentials(String email) {
        LOGGER.warn("Failed login attempt for email={}", email);
        return new AuthenticationFailureException("Invalid email or password.");
    }

    private String generateRefreshToken() {
        byte[] bytes = new byte[64];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes()));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private AuthenticatedUserResponse toResponse(UserAccount user) {
        Set<String> roles = user.getRoles().stream().map(role -> role.getName().name()).collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new AuthenticatedUserResponse(
                user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(), user.getPhone(), roles
        );
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
