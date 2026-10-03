package com.coretrust.bank.beneficiary.event;

public record BeneficiaryRemovedEvent(
        String customerName,
        String customerEmail,
        String beneficiaryName,
        String accountNumber,
        String removedBy      // customer-facing, e.g. "you" or "CoreTrust Support, at your request"
) {
}