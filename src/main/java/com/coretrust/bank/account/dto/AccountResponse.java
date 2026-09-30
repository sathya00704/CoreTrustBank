package com.coretrust.bank.account.dto;

import com.coretrust.bank.account.entity.Account;
import com.coretrust.bank.account.entity.AccountStatus;
import com.coretrust.bank.account.entity.AccountType;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountResponse(
        Long id,
        String accountNumber,
        Long ownerId,
        AccountType accountType,
        BigDecimal balance,
        String currency,
        AccountStatus status,
        Instant createdAt
) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(account.getId(), account.getAccountNumber(), account.getUser().getId(),
                account.getAccountType(), account.getBalance(), account.getCurrency(),
                account.getStatus(), account.getCreatedAt());
    }
}