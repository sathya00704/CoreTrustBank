package com.coretrust.bank.beneficiary.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AssistedBeneficiaryOtpRequest(
        @NotBlank @Email String customerEmail,
        @NotNull @Valid BeneficiaryRequest beneficiary
) {
}