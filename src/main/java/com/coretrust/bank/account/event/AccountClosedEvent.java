package com.coretrust.bank.account.event;

public record AccountClosedEvent(
        String accountNumber,
        String customerName,
        String customerEmail,
        String reason
) {
}