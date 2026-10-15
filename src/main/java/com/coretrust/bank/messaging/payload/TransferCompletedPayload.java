package com.coretrust.bank.messaging.payload;

import java.math.BigDecimal;

public record TransferCompletedPayload(
        Long transactionId,
        String transactionReference,
        String fromAccountNumber,
        String toAccountNumber,
        BigDecimal amount,
        String currency,
        Long initiatedById
) {
}