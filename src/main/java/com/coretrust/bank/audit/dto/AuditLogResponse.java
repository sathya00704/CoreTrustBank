package com.coretrust.bank.audit.dto;

import com.coretrust.bank.audit.entity.AuditLog;

import java.time.Instant;

public record AuditLogResponse(
        Long id,
        Long userId,
        String actorEmail,
        String action,
        String entityType,
        String entityId,
        String description,
        Instant timestamp,
        String ipAddress
) {
    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(log.getId(), log.getUserId(), log.getActorEmail(), log.getAction(),
                log.getEntityType(), log.getEntityId(), log.getDescription(), log.getTimestamp(), log.getIpAddress());
    }
}