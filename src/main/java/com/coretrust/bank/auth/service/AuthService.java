package com.coretrust.bank.auth.service;

import com.coretrust.bank.audit.entity.AuditAction;
import com.coretrust.bank.audit.entity.AuditEntityType;
import com.coretrust.bank.audit.service.AuditService;
import com.coretrust.bank.auth.dto.AuthResponse;
import com.coretrust.bank.auth.dto.LoginRequest;
import com.coretrust.bank.auth.dto.RefreshTokenRequest;
import com.coretrust.bank.auth.dto.RegisterRequest;
import com.coretrust.bank.auth.entity.RefreshToken;
import com.coretrust.bank.auth.repository.RefreshTokenRepository;
import com.coretrust.bank.common.exception.BusinessException;
import com.coretrust.bank.common.exception.ErrorCode;
import com.coretrust.bank.common.exception.ResourceNotFoundException;
import com.coretrust.bank.config.JwtProperties;
import com.coretrust.bank.security.JwtService;
import com.coretrust.bank.security.SecurityUser;
import com.coretrust.bank.user.dto.UserResponse;
import com.coretrust.bank.user.entity.Role;
import com.coretrust.bank.user.entity.User;
import com.coretrust.bank.user.entity.UserStatus;
import com.coretrust.bank.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final AuditService auditService;

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());

        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "Email is already registered.");
        }
        if (userRepository.existsByPhone(request.phone())) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "Phone number is already registered.");
        }

        User user = new User();
        user.setName(request.name().trim());
        user.setEmail(email);
        user.setPhone(request.phone());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(Role.CUSTOMER);
        user.setStatus(UserStatus.ACTIVE);

        User saved = userRepository.save(user);
        log.info("User registered: id={}", saved.getId());
        auditService.record(saved.getId(), saved.getEmail(), AuditAction.USER_REGISTERED,
                AuditEntityType.USER, saved.getId(), "User registered");
        return UserResponse.from(saved);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email, request.password()));
        } catch (AuthenticationException ex) {
            auditService.recordIndependently(null, email, AuditAction.LOGIN_FAILED, AuditEntityType.USER, null,
                    "Failed login (%s)".formatted(ex.getClass().getSimpleName()));
            throw ex;
        }

        SecurityUser principal = (SecurityUser) authentication.getPrincipal();
        User user = userRepository.findById(principal.id())
                .orElseThrow(() -> new ResourceNotFoundException("User", principal.id()));
        Instant now = Instant.now();

        // Checked only AFTER the password is verified, so strangers can't probe who is online
        if (user.hasActiveSession(now)) {
            if (!request.forceLogin()) {
                throw new BusinessException(ErrorCode.SESSION_ALREADY_ACTIVE,
                        "You are already logged in on another tab or device. "
                                + "Log in with forceLogin=true to end that session and continue here.");
            }
            auditService.record(user.getId(), user.getEmail(), AuditAction.SESSION_TERMINATED,
                    AuditEntityType.USER, user.getId(), "Previous session ended by a new login");
        }

        // Single session: nothing from earlier logins stays usable
        refreshTokenRepository.revokeAllActiveByUserId(user.getId());

        JwtProperties.SessionPolicy policy = jwtProperties.policyFor(user.getRole());
        Instant tokenExpiresAt = now.plus(policy.tokenTtl());
        Instant sessionExpiresAt = now.plus(policy.maxLifetime());
        String sessionId = UUID.randomUUID().toString();
        user.startSession(sessionId, tokenExpiresAt);

        log.info("User logged in: id={}", user.getId());
        auditService.record(user.getId(), user.getEmail(), AuditAction.LOGIN_SUCCESS,
                AuditEntityType.USER, user.getId(), "Login successful");
        return issueTokens(user, sessionId, tokenExpiresAt, sessionExpiresAt, false);
    }

    /**
     * Extends the session once, up to its fixed end.
     * noRollbackFor: writes made before a BusinessException (e.g. on token reuse) must be kept.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(sha256(request.refreshToken()))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN, "Refresh token is invalid."));

        User user = stored.getUser();

        // Checked first: an old tab's token after logout or a newer login is not a theft signal
        if (!stored.getSessionId().equals(user.getActiveSessionId())) {
            throw new BusinessException(ErrorCode.SESSION_REVOKED,
                    "This session is no longer active. Please log in again.");
        }
        if (stored.isRevoked()) {
            int revoked = refreshTokenRepository.revokeAllActiveByUserId(user.getId());
            user.endSession();   // also kills the current access token
            log.warn("Refresh token reuse detected for user id={}; revoked {} active sessions", user.getId(), revoked);
            auditService.record(user.getId(), user.getEmail(), AuditAction.REFRESH_TOKEN_REUSE_DETECTED,
                    AuditEntityType.USER, user.getId(),
                    "Revoked refresh token reused; session ended and %d tokens revoked".formatted(revoked));
            throw new BusinessException(ErrorCode.INVALID_TOKEN, "Refresh token has been revoked.");
        }
        if (!stored.getExpiresAt().isAfter(Instant.now())) {
            throw new BusinessException(ErrorCode.SESSION_EXPIRED, "Session has expired. Please log in again.");
        }
        if (stored.isExtended()) {
            throw new BusinessException(ErrorCode.SESSION_EXPIRED,
                    "Session has already been extended once. Please log in again.");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED, "Your account is inactive or locked.");
        }

        stored.setRevoked(true);

        Instant sessionEnd = stored.getSessionExpiresAt();
        user.extendSession(sessionEnd);
        log.info("Session extended for user id={} until {}", user.getId(), sessionEnd);
        auditService.record(user.getId(), user.getEmail(), AuditAction.SESSION_EXTENDED,
                AuditEntityType.USER, user.getId(), "Session extended until " + sessionEnd);
        return issueTokens(user, stored.getSessionId(), sessionEnd, sessionEnd, true);
    }

    /** Idempotent: an unknown or already-revoked token still returns success. */
    @Transactional
    public void logout(RefreshTokenRequest request) {
        refreshTokenRepository.findByTokenHash(sha256(request.refreshToken()))
                .filter(token -> !token.isRevoked())
                .ifPresent(token -> {
                    token.setRevoked(true);
                    User user = token.getUser();
                    if (token.getSessionId().equals(user.getActiveSessionId())) {
                        user.endSession();   // access token stops working immediately
                    }
                    auditService.record(user.getId(), user.getEmail(), AuditAction.LOGOUT,
                            AuditEntityType.USER, user.getId(), "Logged out");
                });
    }

    // ---------- helpers ----------

    private AuthResponse issueTokens(User user, String sessionId, Instant tokenExpiresAt,
                                     Instant sessionExpiresAt, boolean extended) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getRole(), sessionId, tokenExpiresAt);
        String rawRefreshToken = generateRefreshToken();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setSessionId(sessionId);
        refreshToken.setTokenHash(sha256(rawRefreshToken));
        refreshToken.setExpiresAt(tokenExpiresAt);
        refreshToken.setSessionExpiresAt(sessionExpiresAt);
        refreshToken.setExtended(extended);
        refreshTokenRepository.save(refreshToken);

        long expiresIn = Duration.between(Instant.now(), tokenExpiresAt).toSeconds();
        boolean extendable = !extended && tokenExpiresAt.isBefore(sessionExpiresAt);

        return new AuthResponse(accessToken, rawRefreshToken, "Bearer",
                expiresIn, tokenExpiresAt, sessionExpiresAt, extendable);
    }

    private static String generateRefreshToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}