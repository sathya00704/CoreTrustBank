package com.coretrust.bank.beneficiary.dto;

/** For autofill before adding. Name is masked; null for other banks (we can't look them up). */
public record BeneficiaryLookupResponse(
        boolean internal,
        String bankName,
        String accountHolderName
) {
}