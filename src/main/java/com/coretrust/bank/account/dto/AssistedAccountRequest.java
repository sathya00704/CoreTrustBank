package com.coretrust.bank.account.dto;

import com.coretrust.bank.account.entity.AccountType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Must repeat the same customer and account type the OTP was issued for. */
public record AssistedAccountRequest(
        @NotBlank @Email String customerEmail,
        @NotNull AccountType accountType,
        @NotNull Long otpId,
        @NotBlank @Pattern(regexp = "^\\d{6}$", message = "must be a 6-digit code") String otpCode
) {
    @Override
    public String toString() {
        return "AssistedAccountRequest[customerEmail=%s, accountType=%s, otpId=%d, otpCode=***]"
                .formatted(customerEmail, accountType, otpId);
    }
}