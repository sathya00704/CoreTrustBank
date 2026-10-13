package com.coretrust.bank.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EmailChangeRequest(
        @NotBlank @Email @Size(max = 150) String newEmail,
        @NotNull Long otpId,
        @NotBlank @Pattern(regexp = "^\\d{6}$", message = "must be a 6-digit code") String otpCode
) {
    @Override
    public String toString() {
        return "EmailChangeRequest[newEmail=%s, otpId=%d, otpCode=***]".formatted(newEmail, otpId);
    }
}