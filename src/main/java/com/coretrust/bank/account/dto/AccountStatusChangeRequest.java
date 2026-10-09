package com.coretrust.bank.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * reason: internal, recorded in the audit log (mandatory).
 * customerMessage: shown to the customer in the email (optional; a safe default is used if empty).
 */
public record AccountStatusChangeRequest(
        @NotBlank @Size(max = 255) String reason,
        @Size(max = 255) String customerMessage
) {
}