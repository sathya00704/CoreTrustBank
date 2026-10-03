package com.coretrust.bank.beneficiary.event;

public record BeneficiaryAddedEvent(
        String customerName,
        String customerEmail,
        String beneficiaryName,
        String accountNumber,
        String bankName,
        String addedBy      // customer-facing, e.g. "you" or "CoreTrust Support, at your request"
) {
}