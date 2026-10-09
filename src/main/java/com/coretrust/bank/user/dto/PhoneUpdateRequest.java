package com.coretrust.bank.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record PhoneUpdateRequest(
        @NotBlank @Email String customerEmail,

        @NotBlank @Pattern(regexp = "^[6-9]\\d{9}$", message = "must be a valid 10-digit Indian mobile number")
        String newPhone,

        @NotNull Long otpId,

        @NotBlank @Pattern(regexp = "^\\d{6}$", message = "must be a 6-digit code")
        String otpCode
) {
    @Override
    public String toString() {
        return "PhoneUpdateRequest[customerEmail=%s, newPhone=%s, otpId=%d, otpCode=***]"
                .formatted(customerEmail, newPhone, otpId);
    }
}