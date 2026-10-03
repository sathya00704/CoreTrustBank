package com.coretrust.bank.beneficiary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeactivateBeneficiaryRequest(@NotBlank @Size(max = 255) String reason) {
}