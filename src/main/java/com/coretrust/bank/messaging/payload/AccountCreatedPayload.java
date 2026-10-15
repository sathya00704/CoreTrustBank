package com.coretrust.bank.messaging.payload;

public record AccountCreatedPayload(
        Long accountId,
        String accountNumber,
        Long ownerId,
        String accountType
) {
}