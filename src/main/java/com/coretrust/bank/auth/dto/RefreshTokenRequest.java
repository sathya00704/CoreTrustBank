package com.coretrust.bank.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Used by both /refresh and /logout. */
public record RefreshTokenRequest(@NotBlank String refreshToken) {

    @Override
    public String toString() {
        return "RefreshTokenRequest[refreshToken=***]";
    }
}