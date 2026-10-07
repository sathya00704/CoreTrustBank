package com.coretrust.bank.audit.controller;

import com.coretrust.bank.audit.dto.AuditLogFilter;
import com.coretrust.bank.audit.dto.AuditLogResponse;
import com.coretrust.bank.audit.service.AuditLogQueryService;
import com.coretrust.bank.common.dto.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Audit logs", description = "Search the audit trail (admins, or support with VIEW_AUDIT_LOGS)")
@RestController
@RequestMapping("/api/v1/audit-logs")
@PreAuthorize("hasAuthority('PERM_VIEW_AUDIT_LOGS')")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogQueryService auditLogQueryService;

    @GetMapping
    public PageResponse<AuditLogResponse> search(
            @Valid @ParameterObject AuditLogFilter filter,
            @ParameterObject @PageableDefault(size = 50, sort = "timestamp", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return auditLogQueryService.search(filter, pageable);
    }
}