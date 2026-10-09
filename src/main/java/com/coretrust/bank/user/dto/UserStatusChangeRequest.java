package com.coretrust.bank.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** reason: internal (audit log). customerMessage: optional, shown in the lock email. */
public record UserStatusChangeRequest(
        @NotBlank @Size(max = 255) String reason,
        @Size(max = 255) String customerMessage
) {
}