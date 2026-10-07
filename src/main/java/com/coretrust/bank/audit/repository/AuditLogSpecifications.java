package com.coretrust.bank.audit.repository;

import com.coretrust.bank.audit.dto.AuditLogFilter;
import com.coretrust.bank.audit.entity.AuditLog;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AuditLogSpecifications {

    private AuditLogSpecifications() {
    }

    public static Specification<AuditLog> matching(AuditLogFilter f, ZoneId zone) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (f.userId() != null) {
                predicates.add(cb.equal(root.get("userId"), f.userId()));
            }
            if (f.actorEmail() != null && !f.actorEmail().isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("actorEmail")),
                        f.actorEmail().trim().toLowerCase(Locale.ROOT)));
            }
            if (f.action() != null) {
                predicates.add(cb.equal(root.get("action"), f.action().name()));
            }
            if (f.entityType() != null) {
                predicates.add(cb.equal(root.get("entityType"), f.entityType().name()));
            }
            if (f.entityId() != null && !f.entityId().isBlank()) {
                predicates.add(cb.equal(root.get("entityId"), f.entityId().trim()));
            }
            if (f.fromDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("timestamp"),
                        f.fromDate().atStartOfDay(zone).toInstant()));
            }
            if (f.toDate() != null) {
                predicates.add(cb.lessThan(root.<Instant>get("timestamp"),
                        f.toDate().plusDays(1).atStartOfDay(zone).toInstant()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}