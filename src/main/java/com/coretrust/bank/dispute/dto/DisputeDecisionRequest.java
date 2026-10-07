package com.coretrust.bank.dispute.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DisputeDecisionRequest(@NotBlank @Size(max = 255) String note) {
}