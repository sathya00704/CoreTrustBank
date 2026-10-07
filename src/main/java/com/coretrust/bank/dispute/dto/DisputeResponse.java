package com.coretrust.bank.dispute.dto;

import com.coretrust.bank.dispute.entity.Dispute;
import com.coretrust.bank.dispute.entity.DisputeCategory;
import com.coretrust.bank.dispute.entity.DisputeStatus;
import com.coretrust.bank.transaction.entity.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

public record DisputeResponse(
        Long id,
        Long transactionId,
        String transactionReference,
        TransactionType transactionType,
        BigDecimal amount,
        DisputeCategory category,
        String description,
        DisputeStatus status,
        String raisedByEmail,
        Instant raisedAt,
        String resolvedByEmail,
        String resolutionNote,
        Instant resolvedAt
) {
    public static DisputeResponse from(Dispute d) {
        var t = d.getTransaction();
        return new DisputeResponse(d.getId(), t.getId(), t.getTransactionReference(), t.getType(), t.getAmount(),
                d.getCategory(), d.getDescription(), d.getStatus(), d.getRaisedByEmail(), d.getCreatedAt(),
                d.getResolvedByEmail(), d.getResolutionNote(), d.getResolvedAt());
    }
}