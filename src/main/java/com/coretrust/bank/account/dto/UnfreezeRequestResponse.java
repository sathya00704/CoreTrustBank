package com.coretrust.bank.account.dto;

import com.coretrust.bank.account.entity.UnfreezeRequest;
import com.coretrust.bank.account.entity.UnfreezeRequestStatus;

import java.time.Instant;

public record UnfreezeRequestResponse(
        Long id,
        Long accountId,
        String accountNumber,
        UnfreezeRequestStatus status,
        String reason,
        String requestedByEmail,
        Instant requestedAt,
        String reviewedByEmail,
        String reviewNote,
        Instant reviewedAt
) {
    public static UnfreezeRequestResponse from(UnfreezeRequest r) {
        return new UnfreezeRequestResponse(r.getId(), r.getAccount().getId(), r.getAccount().getAccountNumber(),
                r.getStatus(), r.getReason(), r.getRequestedByEmail(), r.getCreatedAt(),
                r.getReviewedByEmail(), r.getReviewNote(), r.getReviewedAt());
    }
}