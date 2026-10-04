package com.coretrust.bank.transfer.dto;

import com.coretrust.bank.transaction.entity.Transaction;
import com.coretrust.bank.transaction.entity.TransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record TransferResponse(
        Long id,
        String transactionReference,
        String fromAccountNumber,
        String toAccountNumber,
        BigDecimal amount,
        String currency,
        TransactionStatus status,
        String description,
        Instant createdAt
) {
    public static TransferResponse from(Transaction t) {
        return new TransferResponse(t.getId(), t.getTransactionReference(),
                t.getFromAccount().getAccountNumber(), t.getToAccount().getAccountNumber(),
                t.getAmount(), t.getCurrency(), t.getStatus(), t.getDescription(), t.getCreatedAt());
    }
}