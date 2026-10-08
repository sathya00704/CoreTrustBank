package com.coretrust.bank.auth.dto;

import com.coretrust.bank.common.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;

public record SetPasswordRequest(
        @NotBlank String token,
        @StrongPassword String newPassword
) {
    @Override
    public String toString() {
        return "SetPasswordRequest[token=***, newPassword=***]";
    }
}