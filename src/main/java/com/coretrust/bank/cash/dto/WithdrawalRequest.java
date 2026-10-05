package com.coretrust.bank.cash.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** customerEmail, accountNumber and amount must match the OTP request exactly. */
public record WithdrawalRequest(
        @NotBlank @Email String customerEmail,

        @NotBlank @Pattern(regexp = "^\\d{9,18}$", message = "must be 9 to 18 digits")
        String accountNumber,

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
        return "WithdrawalRequest[customerEmail=%s, accountNumber=%s, amount=%s, otpId=%d, otpCode=***]"
                .formatted(customerEmail, accountNumber, amount, otpId);
    }
}