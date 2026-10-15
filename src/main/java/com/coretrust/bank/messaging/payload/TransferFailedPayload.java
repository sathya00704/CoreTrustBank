package com.coretrust.bank.messaging.payload;

import java.math.BigDecimal;

/** fromAccount is whatever the caller sent (an id for customers, a number for assisted transfers). */
public record TransferFailedPayload(
        Long initiatedById,
        String fromAccount,
        String toAccountNumber,
        BigDecimal amount,
        String errorCode,
        String reason
) {
}