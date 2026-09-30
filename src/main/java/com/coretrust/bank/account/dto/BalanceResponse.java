package com.coretrust.bank.account.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record BalanceResponse(
        String accountNumber,
        BigDecimal balance,
        String currency,
        Instant asOf //asOf tells the client when the balance was read, since it can change right after.
) {
}