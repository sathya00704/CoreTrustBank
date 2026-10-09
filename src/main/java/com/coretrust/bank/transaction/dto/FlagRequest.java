package com.coretrust.bank.transaction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FlagRequest(@NotBlank @Size(max = 255) String reason) {
}