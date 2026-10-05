package com.coretrust.bank.transaction.dto;

import com.coretrust.bank.transaction.entity.ReversalRequest;
import com.coretrust.bank.transaction.entity.ReversalRequestStatus;
import com.coretrust.bank.transaction.entity.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

public record ReversalRequestResponse(
        Long id,
        Long transactionId,
        String transactionReference,
        TransactionType transactionType,
        BigDecimal amount,
        ReversalRequestStatus status,
        String reason,
        String requestedByEmail,
        Instant requestedAt,
        String reviewedByEmail,
        String reviewNote,
        Instant reviewedAt,
        String reversalReference
) {
    public static ReversalRequestResponse from(ReversalRequest r) {
        var t = r.getTransaction();
        return new ReversalRequestResponse(r.getId(), t.getId(), t.getTransactionReference(), t.getType(),
                t.getAmount(), r.getStatus(), r.getReason(), r.getRequestedByEmail(), r.getCreatedAt(),
                r.getReviewedByEmail(), r.getReviewNote(), r.getReviewedAt(), r.getReversalReference());
    }
}