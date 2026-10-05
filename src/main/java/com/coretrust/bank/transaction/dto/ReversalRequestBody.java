package com.coretrust.bank.transaction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Used for raising (reason) and for reviewing (note). */
public record ReversalRequestBody(@NotBlank @Size(max = 255) String text) {
}