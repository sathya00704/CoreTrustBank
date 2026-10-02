package com.coretrust.bank.account.event;

public record AccountUnfrozenEvent(
        String accountNumber,
        String customerName,
        String customerEmail,
        String unfrozenBy,    // customer-facing description, e.g. "you, using self-service"
        String reason
) {
}