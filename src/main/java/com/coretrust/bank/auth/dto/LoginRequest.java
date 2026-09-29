package com.coretrust.bank.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
) {
    @Override
    public String toString() {
        return "LoginRequest[email=%s, password=***]".formatted(email);
    }
}