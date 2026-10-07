package com.coretrust.bank.statement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public record StatementEmailRequest(
        @NotBlank @Pattern(regexp = "^\\d{9,18}$", message = "must be 9 to 18 digits")
        String accountNumber,

        @NotNull LocalDate fromDate,
        @NotNull LocalDate toDate
) {
}