package com.coretrust.bank.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailChangeOtpRequest(
        @NotBlank @Email @Size(max = 150) String newEmail,
        @NotBlank String currentPassword
) {
    @Override
    public String toString() {
        return "EmailChangeOtpRequest[newEmail=%s, currentPassword=***]".formatted(newEmail);
    }
}