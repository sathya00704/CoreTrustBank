package com.coretrust.bank.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Both fields optional: only non-null values are updated (PATCH semantics). */
public record UpdateProfileRequest(
        @Size(min = 2, max = 100) String name,
        @Pattern(regexp = "^[6-9]\\d{9}$", message = "must be a valid 10-digit Indian mobile number") String phone
) {
}