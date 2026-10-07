package com.coretrust.bank.audit.service;

import com.coretrust.bank.audit.dto.AuditLogFilter;
import com.coretrust.bank.audit.dto.AuditLogResponse;
import com.coretrust.bank.audit.entity.AuditAction;
import com.coretrust.bank.audit.entity.AuditEntityType;
import com.coretrust.bank.audit.repository.AuditLogRepository;
import com.coretrust.bank.audit.repository.AuditLogSpecifications;
import com.coretrust.bank.common.dto.PageResponse;
import com.coretrust.bank.common.exception.BusinessException;
import com.coretrust.bank.common.exception.ErrorCode;
import com.coretrust.bank.common.util.SortValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuditLogQueryService {

    private static final Set<String> SORTABLE = Set.of("timestamp");

    private final AuditLogRepository auditLogRepository;
    private final AuditService auditService;

    @Value("${app.timezone}")
    private String timezone;

    /** Not read-only: every search of the audit trail is itself recorded. */
    @Transactional
    public PageResponse<AuditLogResponse> search(AuditLogFilter filter, Pageable pageable) {
        SortValidator.requireAllowed(pageable.getSort(), SORTABLE);
        if (filter.fromDate() != null && filter.toDate() != null && filter.fromDate().isAfter(filter.toDate())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "fromDate must not be after toDate.");
        }

        var page = auditLogRepository.findAll(AuditLogSpecifications.matching(filter, ZoneId.of(timezone)), pageable);
        auditService.record(AuditAction.AUDIT_LOGS_VIEWED, AuditEntityType.USER, null,
                "Audit logs searched with " + filter);
        return PageResponse.from(page.map(AuditLogResponse::from));
    }
}