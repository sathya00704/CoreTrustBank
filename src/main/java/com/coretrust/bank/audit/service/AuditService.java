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
    //Used for normal actions by a logged-in user: creating an account, freezing, changing a password.
    //The caller doesn't need to pass the user.
    @Transactional
    public void record(AuditAction action, AuditEntityType entityType, Object entityId, String description) {
        SecurityUser actor = currentUser();
        Long userId = actor == null ? null : actor.id();
        String email = actor == null ? null : actor.email();
        auditLogRepository.save(build(userId, email, action, entityType, entityId, description));
    }

    /** Explicit actor: for events where the user isn't in the SecurityContext (register, login, refresh). */
    //The caller says who the actor is.
    //Needed for register, login, refresh, and logout, because nobody is in the SecurityContext at that point.
    @Transactional
    public void record(Long userId, String actorEmail, AuditAction action, AuditEntityType entityType,
                       Object entityId, String description) {
        auditLogRepository.save(build(userId, actorEmail, action, entityType, entityId, description));
    }

    /** Own transaction: the entry is kept even if the caller's transaction rolls back. Used for failures. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependently(Long userId, String actorEmail, AuditAction action,
                                    AuditEntityType entityType, Object entityId, String description) {
        auditLogRepository.save(build(userId, actorEmail, action, entityType, entityId, description));
    }

    private AuditLog build(Long userId, String actorEmail, AuditAction action, AuditEntityType entityType,
                           Object entityId, String description) {
        AuditLog entry = new AuditLog();
        entry.setUserId(userId);
        entry.setActorEmail(actorEmail);
        entry.setAction(action.name());
        entry.setEntityType(entityType.name());
        entry.setEntityId(entityId == null ? null : String.valueOf(entityId));
        entry.setDescription(truncate(description));
        entry.setIpAddress(currentIpAddress());
        return entry;
    }

    private static SecurityUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.getPrincipal() instanceof SecurityUser user) ? user : null;
    }

    //0:0:0:0:0:0:0:1 for local
    private static String currentIpAddress() {
        return (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)
                ? attrs.getRequest().getRemoteAddr()
                : null;
    }

    //Reduces length if more than the max description length
    private static String truncate(String value) {
        return (value == null || value.length() <= MAX_DESCRIPTION_LENGTH)
                ? value : value.substring(0, MAX_DESCRIPTION_LENGTH);
    }
}