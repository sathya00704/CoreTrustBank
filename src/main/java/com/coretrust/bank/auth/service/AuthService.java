package com.coretrust.bank.auth.service;

import com.coretrust.bank.auth.dto.AuthResponse;
import com.coretrust.bank.auth.dto.LoginRequest;
import com.coretrust.bank.auth.dto.RefreshTokenRequest;
import com.coretrust.bank.auth.dto.RegisterRequest;
import com.coretrust.bank.auth.entity.RefreshToken;
import com.coretrust.bank.auth.repository.RefreshTokenRepository;
import com.coretrust.bank.common.exception.BusinessException;
import com.coretrust.bank.common.exception.ErrorCode;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;

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
        user.setRole(Role.CUSTOMER);              // never taken from the request
        user.setStatus(UserStatus.ACTIVE);

        User saved = userRepository.save(user);
        log.info("User registered: id={}", saved.getId());
        return UserResponse.from(saved);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(normalizeEmail(request.email()), request.password()));

        SecurityUser principal = (SecurityUser) authentication.getPrincipal();
        JwtProperties.SessionPolicy policy = jwtProperties.policyFor(principal.role());

        Instant now = Instant.now();
        Instant tokenExpiresAt = now.plus(policy.tokenTtl());         // e.g. minute 15
        Instant sessionExpiresAt = now.plus(policy.maxLifetime());    // e.g. minute 30

        log.info("User logged in: id={}", principal.id());
        return issueTokens(principal.id(), principal.role(), tokenExpiresAt, sessionExpiresAt, false);
    }

    /**
     * Rotation: every refresh revokes the old refresh token and issues a new one.
     * noRollbackFor: the "revoke all sessions" write on token reuse must survive the exception we throw.
     */
    /**
     * Extends the session once. The extension runs until the session's fixed end.
     * noRollbackFor: the "revoke all sessions" write on token reuse must survive the exception.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(sha256(request.refreshToken()))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN, "Refresh token is invalid."));

        User user = stored.getUser();

        if (stored.isRevoked()) {
            int revoked = refreshTokenRepository.revokeAllActiveByUserId(user.getId());
            log.warn("Refresh token reuse detected for user id={}; revoked {} active sessions", user.getId(), revoked);
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

        // Fixed end: the extended token lasts until the original session deadline, not "15 minutes from now"
        Instant sessionEnd = stored.getSessionExpiresAt();
        log.info("Session extended for user id={} until {}", user.getId(), sessionEnd);
        return issueTokens(user.getId(), user.getRole(), sessionEnd, sessionEnd, true);
    }

    /** Idempotent: logging out with an unknown or already-revoked token still succeeds. */
    @Transactional
    public void logout(RefreshTokenRequest request) {
        refreshTokenRepository.findByTokenHash(sha256(request.refreshToken()))
                .ifPresent(token -> token.setRevoked(true));
    }

    // ---------- helpers ----------

    private AuthResponse issueTokens(Long userId, Role role, Instant tokenExpiresAt,
                                     Instant sessionExpiresAt, boolean extended) {
        String accessToken = jwtService.generateAccessToken(userId, role, tokenExpiresAt);
        String rawRefreshToken = generateRefreshToken();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(userRepository.getReferenceById(userId));
        refreshToken.setTokenHash(sha256(rawRefreshToken));
        refreshToken.setExpiresAt(tokenExpiresAt);          // extension only possible before this
        refreshToken.setSessionExpiresAt(sessionExpiresAt);
        refreshToken.setExtended(extended);
        refreshTokenRepository.save(refreshToken);

        long expiresIn = Duration.between(Instant.now(), tokenExpiresAt).toSeconds();
        boolean extendable = !extended && tokenExpiresAt.isBefore(sessionExpiresAt);

        return new AuthResponse(accessToken, rawRefreshToken, "Bearer",
                expiresIn, tokenExpiresAt, sessionExpiresAt, extendable);
    }

    private static String generateRefreshToken() {
        byte[] bytes = new byte[32];                 // 256 bits of randomness
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