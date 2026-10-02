package com.coretrust.bank.otp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record OtpVerificationRequest(
        @NotNull Long otpId,
        @NotBlank @Pattern(regexp = "^\\d{6}$", message = "must be a 6-digit code") String otpCode
) {
    @Override
    public String toString() {
        return "OtpVerificationRequest[otpId=%d, otpCode=***]".formatted(otpId);
    }
}