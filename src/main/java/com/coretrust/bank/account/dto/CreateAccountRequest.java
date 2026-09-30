package com.coretrust.bank.account.dto;

import com.coretrust.bank.account.entity.AccountType;
import jakarta.validation.constraints.NotNull;

public record CreateAccountRequest(@NotNull AccountType accountType) {
}