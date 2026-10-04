package com.coretrust.bank.cash.dto;

import com.coretrust.bank.transaction.entity.Transaction;
import com.coretrust.bank.transaction.entity.TransactionStatus;
import com.coretrust.bank.transaction.entity.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

public record CashTransactionResponse(
        Long id,
        String transactionReference,
        TransactionType type,
        String accountNumber,
        BigDecimal amount,
        String currency,
        TransactionStatus status,
        String description,
        Instant createdAt
) {
    public static CashTransactionResponse from(Transaction t) {
        String accountNumber = (t.getType() == TransactionType.DEPOSIT)
                ? t.getToAccount().getAccountNumber()
                : t.getFromAccount().getAccountNumber();
        return new CashTransactionResponse(t.getId(), t.getTransactionReference(), t.getType(), accountNumber,
                t.getAmount(), t.getCurrency(), t.getStatus(), t.getDescription(), t.getCreatedAt());
    }
}