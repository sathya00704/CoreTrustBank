package com.coretrust.bank.account.dto;

import com.coretrust.bank.account.entity.AccountType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AssistedAccountOtpRequest(
        @NotBlank @Email String customerEmail,
        @NotNull AccountType accountType
) {
}