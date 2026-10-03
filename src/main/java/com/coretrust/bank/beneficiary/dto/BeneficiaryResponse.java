package com.coretrust.bank.beneficiary.dto;

import com.coretrust.bank.beneficiary.entity.Beneficiary;
import com.coretrust.bank.beneficiary.entity.BeneficiaryStatus;

import java.time.Instant;

public record BeneficiaryResponse(
        Long id,
        Long ownerId,
        String beneficiaryName,
        String accountNumber,
        String bankName,
        String ifscCode,
        boolean internal,        // true = a CoreTrust account
        BeneficiaryStatus status,
        Instant createdAt
) {
    public static BeneficiaryResponse from(Beneficiary b, boolean internal) {
        return new BeneficiaryResponse(b.getId(), b.getUser().getId(), b.getBeneficiaryName(), b.getAccountNumber(),
                b.getBankName(), b.getIfscCode(), internal, b.getStatus(), b.getCreatedAt());
    }
}