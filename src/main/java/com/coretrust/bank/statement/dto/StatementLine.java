package com.coretrust.bank.statement.dto;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

/** One row: exactly one of debit / credit is set. */
public record StatementLine(
        ZonedDateTime dateTime,
        String reference,
        String narrative,
        BigDecimal debit,
        BigDecimal credit,
        BigDecimal balance
) {
}