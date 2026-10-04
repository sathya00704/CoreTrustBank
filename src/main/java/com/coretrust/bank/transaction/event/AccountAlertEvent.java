package com.coretrust.bank.transaction.event;

import java.math.BigDecimal;

/** One per affected account: a transfer produces a DEBIT alert and a CREDIT alert. */
public record AccountAlertEvent(
        String customerName,
        String customerEmail,
        String accountNumber,
        AlertType type,
        BigDecimal amount,
        String currency,
        BigDecimal balanceAfter,
        String details,
        String reference
) {
}