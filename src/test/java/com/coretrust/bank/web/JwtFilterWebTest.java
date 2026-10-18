package com.coretrust.bank.web;

import com.coretrust.bank.config.SecurityConfig;
import com.coretrust.bank.security.JwtService;
import com.coretrust.bank.security.RestAccessDeniedHandler;
import com.coretrust.bank.security.RestAuthenticationEntryPoint;
import com.coretrust.bank.security.SecurityUser;
import com.coretrust.bank.user.controller.UserController;
import com.coretrust.bank.user.dto.UserResponse;
import com.coretrust.bank.user.entity.Role;
import com.coretrust.bank.user.entity.UserStatus;
import com.coretrust.bank.user.service.UserService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.Set;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class JwtFilterWebTest extends WebSliceTest {

    @MockitoBean
    private UserService userService;

    @Test
    void noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void expiredToken_returnsTokenExpired_soTheClientKnowsToRefresh() throws Exception {
        when(jwtService.parseAccessToken("expired-token"))
                .thenThrow(new ExpiredJwtException(null, null, "expired"));

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer expired-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("TOKEN_EXPIRED"));
    }

    @Test
    void malformedToken_returnsInvalidToken() throws Exception {
        when(jwtService.parseAccessToken("garbage")).thenThrow(new MalformedJwtException("bad"));

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer garbage"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @Test
    void tokenFromAReplacedSession_isRejected() throws Exception {
        when(jwtService.parseAccessToken("old-token"))
                .thenReturn(new JwtService.AccessTokenClaims(7L, "old-session"));
        when(userDetailsService.loadUserById(7L))
                .thenReturn(sessionUser(UserStatus.ACTIVE, "current-session"));

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer old-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("SESSION_REVOKED"));
    }

    @Test
    void lockedUser_isRejectedEvenWithAValidToken() throws Exception {
        when(jwtService.parseAccessToken("valid-token"))
                .thenReturn(new JwtService.AccessTokenClaims(7L, "session-7"));
        when(userDetailsService.loadUserById(7L)).thenReturn(sessionUser(UserStatus.LOCKED, "session-7"));

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_DISABLED"));
    }

    @Test
    void validToken_reachesTheController_asThatUser() throws Exception {
        when(jwtService.parseAccessToken("valid-token"))
                .thenReturn(new JwtService.AccessTokenClaims(7L, "session-7"));
        when(userDetailsService.loadUserById(7L)).thenReturn(sessionUser(UserStatus.ACTIVE, "session-7"));
        when(userService.getProfile(7L)).thenReturn(new UserResponse(7L, "Sathya", "sathya@test.com",
                "9876543210", Role.CUSTOMER, UserStatus.ACTIVE, Instant.now()));

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("sathya@test.com"));

        verify(userService).getProfile(7L);   // the id came from the token, not from the URL
    }

    private static SecurityUser sessionUser(UserStatus status, String activeSessionId) {
        return new SecurityUser(7L, "sathya@test.com", null, Role.CUSTOMER, status, activeSessionId, Set.of());
    }
}