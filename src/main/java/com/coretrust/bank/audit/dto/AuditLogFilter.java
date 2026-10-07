package com.coretrust.bank.audit.dto;

import com.coretrust.bank.audit.entity.AuditAction;
import com.coretrust.bank.audit.entity.AuditEntityType;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** All optional, e.g. ?actorEmail=support@test.com&action=ACCOUNT_FROZEN&fromDate=2026-10-01 */
public record AuditLogFilter(
        Long userId,
        @Size(max = 150) String actorEmail,
        AuditAction action,
        AuditEntityType entityType,
        @Size(max = 50) String entityId,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate
) {
}