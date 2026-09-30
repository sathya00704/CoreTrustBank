package com.coretrust.bank.audit.service;

import com.coretrust.bank.audit.entity.AuditAction;
import com.coretrust.bank.audit.entity.AuditEntityType;
import com.coretrust.bank.audit.entity.AuditLog;
import com.coretrust.bank.audit.repository.AuditLogRepository;
import com.coretrust.bank.security.SecurityUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
@RequiredArgsConstructor
public class AuditService {

    private static final int MAX_DESCRIPTION_LENGTH = 1000;

    private final AuditLogRepository auditLogRepository;

    /** Actor = the logged-in user. Joins the caller's transaction. */
    @Transactional
    public void record(AuditAction action, AuditEntityType entityType, Object entityId, String description) {
        auditLogRepository.save(build(currentUserId(), action, entityType, entityId, description));
    }

    /** Explicit actor: for events where the user isn't in the SecurityContext yet (register, login). */
    @Transactional
    public void record(Long userId, AuditAction action, AuditEntityType entityType, Object entityId, String description) {
        auditLogRepository.save(build(userId, action, entityType, entityId, description));
    }

    /** Own transaction: the entry is kept even if the caller's transaction rolls back. Used for failures. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependently(Long userId, AuditAction action, AuditEntityType entityType,
                                    Object entityId, String description) {
        auditLogRepository.save(build(userId, action, entityType, entityId, description));
    }

    private AuditLog build(Long userId, AuditAction action, AuditEntityType entityType,
                           Object entityId, String description) {
        AuditLog entry = new AuditLog();
        entry.setUserId(userId);
        entry.setAction(action.name());
        entry.setEntityType(entityType.name());
        entry.setEntityId(entityId == null ? null : String.valueOf(entityId));
        entry.setDescription(truncate(description));
        entry.setIpAddress(currentIpAddress());
        return entry;
    }

    private static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.getPrincipal() instanceof SecurityUser user) ? user.id() : null;
    }

    private static String currentIpAddress() {
        return (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)
                ? attrs.getRequest().getRemoteAddr()
                : null;   // e.g. scheduled jobs: no HTTP request
    }

    private static String truncate(String value) {
        return (value == null || value.length() <= MAX_DESCRIPTION_LENGTH)
                ? value : value.substring(0, MAX_DESCRIPTION_LENGTH);
    }
}