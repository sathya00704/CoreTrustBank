package com.coretrust.bank.auth.controller;

import com.coretrust.bank.auth.dto.*;
import com.coretrust.bank.auth.service.AuthService;
import com.coretrust.bank.common.dto.MessageResponse;
import com.coretrust.bank.user.dto.UserResponse;
import com.coretrust.bank.auth.dto.SetPasswordRequest;
import com.coretrust.bank.auth.service.PasswordSetupService;
import com.coretrust.bank.auth.dto.ForgotPasswordRequest;
import com.coretrust.bank.auth.dto.ResetPasswordRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Authentication", description = "Register, login, session extension and logout")
@SecurityRequirements   // empty = these endpoints need no token; removes the lock icon
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    private final PasswordSetupService passwordSetupService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    public MessageResponse logout(@Valid @RequestBody RefreshTokenRequest request) {
        LogoutResult result = authService.logout(request);
        return MessageResponse.of(result == LogoutResult.LOGGED_OUT
                ? "You have been logged out successfully."
                : "You were already logged out.");
    }

    @PostMapping("/set-password")
    public MessageResponse setPassword(@Valid @RequestBody SetPasswordRequest request) {
        passwordSetupService.setPassword(request);
        return MessageResponse.of("Your password has been set. You can now log in.");
    }

    @PostMapping("/forgot-password")
    public MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordSetupService.requestPasswordReset(request.email());
        return MessageResponse.of("If a profile exists for this email, we've sent a password reset link.");
    }

    @PostMapping("/reset-password")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordSetupService.resetPassword(request);
        return MessageResponse.of("Your password has been reset. Please log in with your new password.");
    }
}