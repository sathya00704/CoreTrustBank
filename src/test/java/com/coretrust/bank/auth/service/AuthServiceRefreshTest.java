package com.coretrust.bank.auth.service;

import com.coretrust.bank.audit.service.AuditService;
import com.coretrust.bank.auth.dto.AuthResponse;
import com.coretrust.bank.auth.dto.RefreshTokenRequest;
import com.coretrust.bank.auth.entity.RefreshToken;
import com.coretrust.bank.auth.repository.RefreshTokenRepository;
import com.coretrust.bank.common.exception.ErrorCode;
import com.coretrust.bank.config.JwtProperties;
import com.coretrust.bank.ratelimit.RateLimiter;
import com.coretrust.bank.security.JwtService;
import com.coretrust.bank.user.entity.Role;
import com.coretrust.bank.user.entity.User;
import com.coretrust.bank.user.entity.UserStatus;
import com.coretrust.bank.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceRefreshTest {

    private static final Instant NOW = Instant.parse("2026-10-16T10:00:00Z");
    private static final Instant SESSION_END = NOW.plus(Duration.ofMinutes(20));

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtService jwtService;
    @Mock private JwtProperties jwtProperties;
    @Mock private AuditService auditService;
    @Mock private RateLimiter rateLimiter;
    @Mock private Clock clock;

    @InjectMocks
    private AuthService authService;

    private User user;
    private RefreshToken token;

    @BeforeEach
    void setUp() {
        lenient().when(clock.instant()).thenReturn(NOW);

        user = new User();
        ReflectionTestUtils.setField(user, "id", 7L);
        user.setEmail("sathya@test.com");
        user.setRole(Role.CUSTOMER);
        user.setStatus(UserStatus.ACTIVE);
        user.startSession("session-1", NOW.plus(Duration.ofMinutes(5)));

        token = new RefreshToken();
        token.setUser(user);
        token.setSessionId("session-1");
        token.setTokenHash("hash");
        token.setExpiresAt(NOW.plus(Duration.ofMinutes(5)));
        token.setSessionExpiresAt(SESSION_END);
        token.setExtended(false);

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
    }

    @Test
    void refresh_rotatesTheToken_andExtendsToTheFixedSessionEnd() {
        AuthResponse response = authService.refresh(new RefreshTokenRequest("raw-token"));

        assertThat(token.isRevoked()).isTrue();                          // old token can't be used again
        assertThat(response.accessTokenExpiresAt()).isEqualTo(SESSION_END);
        assertThat(response.sessionExpiresAt()).isEqualTo(SESSION_END);
        assertThat(response.extendable()).isFalse();                     // one extension only
    }

    @Test
    void refresh_withATokenFromAReplacedSession_isRevoked_withoutTreatingItAsTheft() {
        user.startSession("session-2", NOW.plus(Duration.ofMinutes(5)));  // a newer login took over

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest("raw-token")))
                .extracting("errorCode").isEqualTo(ErrorCode.SESSION_REVOKED);
        verify(refreshTokenRepository, never()).revokeAllActiveByUserId(anyLong());
    }

    @Test
    void refresh_reusingARevokedToken_endsTheWholeSession() {
        token.setRevoked(true);

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest("raw-token")))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_TOKEN);
        verify(refreshTokenRepository).revokeAllActiveByUserId(7L);
        assertThat(user.getActiveSessionId()).isNull();
    }

    @Test
    void refresh_afterTheTokenExpired_isRejected() {
        token.setExpiresAt(NOW.minusSeconds(1));

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest("raw-token")))
                .extracting("errorCode").isEqualTo(ErrorCode.SESSION_EXPIRED);
    }

    @Test
    void aSecondExtension_isRejected() {
        token.setExtended(true);

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest("raw-token")))
                .extracting("errorCode").isEqualTo(ErrorCode.SESSION_EXPIRED);
    }
}