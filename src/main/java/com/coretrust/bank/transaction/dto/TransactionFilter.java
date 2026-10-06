package com.coretrust.bank.transaction.dto;

import com.coretrust.bank.transaction.entity.TransactionStatus;
import com.coretrust.bank.transaction.entity.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/** All optional. Bound from query parameters, e.g. ?type=TRANSFER&fromDate=2026-10-01 */
public record TransactionFilter(
        Long accountId,
        Long userId,                       // staff only; ignored for customers
        TransactionType type,
        TransactionStatus status,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
        @DecimalMin("0.00") BigDecimal minAmount,
        @DecimalMin("0.00") BigDecimal maxAmount,
        @Size(max = 100) String search
) {
}