package com.coretrust.bank.transaction.event;

import com.coretrust.bank.transaction.entity.TransactionType;

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
        String reference,
        TransactionType transactionType      // lets the email listener skip transfers (emailed via Kafka)
) {
}