package com.coretrust.bank.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReviewDecisionRequest(@NotBlank @Size(max = 255) String note) {
}