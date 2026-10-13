package com.coretrust.bank.caseupdate.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CaseUpdateRequest(
        @NotBlank @Email String customerEmail,
        @NotNull CaseType caseType,
        Long caseId,                                   // required except for GENERAL
        @NotBlank @Size(max = 400) String message
) {
}