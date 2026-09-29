package com.coretrust.bank.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 2, max = 100)
        String name,

        @NotBlank @Email @Size(max = 150)
        String email,

        @NotBlank
        @Pattern(regexp = "^[6-9]\\d{9}$", message = "must be a valid 10-digit Indian mobile number")
        String phone,

        @NotBlank @Size(min = 8, max = 64)
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
                message = "must contain upper and lower case letters, a digit and a special character")
        String password
) {
    @Override
    public String toString() {
        return "RegisterRequest[name=%s, email=%s, phone=%s, password=***]".formatted(name, email, phone);
    }
}