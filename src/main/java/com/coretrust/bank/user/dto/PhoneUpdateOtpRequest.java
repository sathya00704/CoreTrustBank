package com.coretrust.bank.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PhoneUpdateOtpRequest(
        @NotBlank @Email String customerEmail,

        @NotBlank @Pattern(regexp = "^[6-9]\\d{9}$", message = "must be a valid 10-digit Indian mobile number")
        String newPhone
) {
}