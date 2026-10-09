package com.coretrust.bank.transaction.dto;

import com.coretrust.bank.transaction.entity.Transaction;

import java.time.Instant;

public record TransactionFlagResponse(
        Long transactionId,
        String transactionReference,
        boolean flagged,
        String flagReason,
        String flaggedByEmail,
        Instant flaggedAt
) {
    public static TransactionFlagResponse from(Transaction t) {
        return new TransactionFlagResponse(t.getId(), t.getTransactionReference(), t.isFlagged(),
                t.getFlagReason(), t.getFlaggedByEmail(), t.getFlaggedAt());
    }
}