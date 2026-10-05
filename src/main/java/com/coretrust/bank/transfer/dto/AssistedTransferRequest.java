package com.coretrust.bank.transfer.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Customer, accounts and amount must match the OTP request exactly. */
public record AssistedTransferRequest(
        @NotBlank @Email String customerEmail,

        @NotBlank @Pattern(regexp = "^\\d{9,18}$", message = "must be 9 to 18 digits")
        String fromAccountNumber,

        @NotBlank @Pattern(regexp = "^\\d{9,18}$", message = "must be 9 to 18 digits")
        String toAccountNumber,

        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 13, fraction = 2)
        BigDecimal amount,

        @Size(max = 255)
        String description,

        @NotNull Long otpId,

        @NotBlank @Pattern(regexp = "^\\d{6}$", message = "must be a 6-digit code")
        String otpCode
) {
    @Override
    public String toString() {
        return "AssistedTransferRequest[customerEmail=%s, from=%s, to=%s, amount=%s, otpId=%d, otpCode=***]"
                .formatted(customerEmail, fromAccountNumber, toAccountNumber, amount, otpId);
    }
}