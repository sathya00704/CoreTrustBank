package com.coretrust.bank.beneficiary.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Must repeat exactly the customer and beneficiary details the OTP was issued for. */
public record AssistedBeneficiaryRequest(
        @NotBlank @Email String customerEmail,
        @NotNull @Valid BeneficiaryRequest beneficiary,
        @NotNull Long otpId,
        @NotBlank @Pattern(regexp = "^\\d{6}$", message = "must be a 6-digit code") String otpCode
) {
    @Override
    public String toString() {
        return "AssistedBeneficiaryRequest[customerEmail=%s, beneficiary=%s, otpId=%d, otpCode=***]"
                .formatted(customerEmail, beneficiary, otpId);
    }
}