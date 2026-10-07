package com.coretrust.bank.dispute.dto;

import com.coretrust.bank.dispute.entity.DisputeCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RaiseDisputeRequest(
        @NotNull DisputeCategory category,
        @NotBlank @Size(max = 500) String description
) {
}