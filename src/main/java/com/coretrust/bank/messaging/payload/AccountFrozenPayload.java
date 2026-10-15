package com.coretrust.bank.messaging.payload;

/** The internal freeze reason is deliberately left out: events travel to other systems. */
public record AccountFrozenPayload(
        Long accountId,
        String accountNumber,
        Long ownerId,
        String frozenByRole
) {
}