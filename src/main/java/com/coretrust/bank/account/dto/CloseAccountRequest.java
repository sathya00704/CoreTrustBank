package com.coretrust.bank.account.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CloseAccountRequest(
        @NotBlank @Email String customerEmail,

        @NotBlank @Pattern(regexp = "^\\d{9,18}$", message = "must be 9 to 18 digits")
        String accountNumber,

        @NotBlank @Size(max = 255)
        String reason,

        @NotNull Long otpId,

        @NotBlank @Pattern(regexp = "^\\d{6}$", message = "must be a 6-digit code")
        String otpCode
) {
    @Override
    public String toString() {
        return "CloseAccountRequest[customerEmail=%s, accountNumber=%s, otpId=%d, otpCode=***]"
                .formatted(customerEmail, accountNumber, otpId);
    }
}