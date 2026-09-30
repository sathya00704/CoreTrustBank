package com.coretrust.bank.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Reason is mandatory: every freeze/unfreeze must be justified in the audit log. */
public record AccountStatusChangeRequest(@NotBlank @Size(max = 255) String reason) {
}