package com.coretrust.bank.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.coretrust.bank.common.validation.StrongPassword;

public record RegisterRequest(
        @NotBlank @Size(min = 2, max = 100)
        String name,

        @NotBlank @Email @Size(max = 150)
        String email,

        @NotBlank
        @Pattern(regexp = "^[6-9]\\d{9}$", message = "must be a valid 10-digit Indian mobile number")
        String phone,

        @StrongPassword
        String password
) {
    @Override
    public String toString() {
        return "RegisterRequest[name=%s, email=%s, phone=%s, password=***]".formatted(name, email, phone);
    }
}