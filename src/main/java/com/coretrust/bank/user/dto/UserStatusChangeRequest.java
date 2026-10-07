package com.coretrust.bank.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserStatusChangeRequest(@NotBlank @Size(max = 255) String reason) {
}