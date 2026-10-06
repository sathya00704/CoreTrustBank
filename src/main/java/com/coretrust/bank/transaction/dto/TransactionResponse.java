package com.coretrust.bank.transaction.dto;

import com.coretrust.bank.transaction.entity.TransactionStatus;
import com.coretrust.bank.transaction.entity.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponse(
        Long id,
        String transactionReference,
        TransactionType type,
        TransactionStatus status,
        BigDecimal amount,
        String currency,
        String description,
        String fromAccountNumber,          // null for deposits; masked if not the viewer's
        String fromAccountHolder,
        String toAccountNumber,            // null for withdrawals; masked if not the viewer's
        String toAccountHolder,
        Direction direction,               // relative to the account the row is viewed from
        BigDecimal balanceAfter,           // that account's balance right after this transaction
        String originalTransactionReference, // for REVERSAL rows
        Instant createdAt
) {
}