package com.coretrust.bank.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUnfreezeRequest(
        @NotBlank @Size(max = 255) String reason,
        @NotNull Long otpId,
        @NotBlank @Pattern(regexp = "^\\d{6}$", message = "must be a 6-digit code") String otpCode
) {
    @Override
    public String toString() {
        return "CreateUnfreezeRequest[reason=%s, otpId=%d, otpCode=***]".formatted(reason, otpId);
    }
}