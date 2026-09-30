package com.coretrust.bank.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password,
        boolean forceLogin      // true = end the existing session and continue; defaults to false
) {
    @Override
    public String toString() {
        return "LoginRequest[email=%s, password=***, forceLogin=%s]".formatted(email, forceLogin);
    }
}