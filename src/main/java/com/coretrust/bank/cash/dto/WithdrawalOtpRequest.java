package com.coretrust.bank.cash.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record WithdrawalOtpRequest(
        @NotBlank @Email String customerEmail,

        @NotBlank @Pattern(regexp = "^\\d{9,18}$", message = "must be 9 to 18 digits")
        String accountNumber,

        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 13, fraction = 2)
        BigDecimal amount
) {
}