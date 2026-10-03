package com.coretrust.bank.beneficiary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** For CoreTrust accounts, name and bank are filled in by the server; for other banks both are required. */
public record BeneficiaryRequest(
        @Size(min = 2, max = 100)
        @Pattern(regexp = "^[A-Za-z][A-Za-z .']*$", message = "may contain only letters, spaces, dots and apostrophes")
        String beneficiaryName,

        @NotBlank
        @Pattern(regexp = "^\\d{9,18}$", message = "must be 9 to 18 digits")
        String accountNumber,

        @Size(max = 100)
        String bankName,

        @NotBlank
        @Pattern(regexp = "(?i)^[A-Z]{4}0[A-Z0-9]{6}$", message = "must be a valid IFSC code, e.g. CTRB0001001")
        String ifscCode
) {
}