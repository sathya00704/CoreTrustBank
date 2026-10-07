package com.coretrust.bank.statement.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

public record Statement(
        String bankName,
        String accountHolder,
        String accountNumber,
        String accountType,
        String currency,
        LocalDate fromDate,
        LocalDate toDate,
        ZonedDateTime generatedAt,
        BigDecimal openingBalance,
        BigDecimal totalDebits,
        BigDecimal totalCredits,
        BigDecimal closingBalance,
        List<StatementLine> lines
) {
}