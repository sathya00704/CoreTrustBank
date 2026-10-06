package com.coretrust.bank.transaction.repository;

import com.coretrust.bank.account.entity.Account;
import com.coretrust.bank.transaction.dto.TransactionFilter;
import com.coretrust.bank.transaction.entity.Transaction;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    /**
     * @param restrictToUserId customers: only transactions where they are sender or receiver (null for staff)
     * @param staffUserFilter  staff: optional "transactions of this user" filter (null for customers)
     */
    public static Specification<Transaction> matching(TransactionFilter f, Long restrictToUserId,
                                                      Long staffUserFilter, ZoneId zone) {
        return (root, query, cb) -> {
            // LEFT joins: deposits have no fromAccount, withdrawals have no toAccount
            Join<Transaction, Account> from = root.join("fromAccount", JoinType.LEFT);
            Join<Transaction, Account> to = root.join("toAccount", JoinType.LEFT);
            List<Predicate> predicates = new ArrayList<>();

            Long partyUserId = restrictToUserId != null ? restrictToUserId : staffUserFilter;
            if (partyUserId != null) {
                predicates.add(cb.or(
                        cb.equal(from.get("user").get("id"), partyUserId),
                        cb.equal(to.get("user").get("id"), partyUserId)));
            }
            if (f.accountId() != null) {
                predicates.add(cb.or(
                        cb.equal(from.get("id"), f.accountId()),
                        cb.equal(to.get("id"), f.accountId())));
            }
            if (f.type() != null) {
                predicates.add(cb.equal(root.get("type"), f.type()));
            }
            if (f.status() != null) {
                predicates.add(cb.equal(root.get("status"), f.status()));
            }
            if (f.fromDate() != null) {
                Instant start = f.fromDate().atStartOfDay(zone).toInstant();
                predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), start));
            }
            if (f.toDate() != null) {
                Instant endExclusive = f.toDate().plusDays(1).atStartOfDay(zone).toInstant();
                predicates.add(cb.lessThan(root.<Instant>get("createdAt"), endExclusive));
            }
            if (f.minAmount() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<BigDecimal>get("amount"), f.minAmount()));
            }
            if (f.maxAmount() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<BigDecimal>get("amount"), f.maxAmount()));
            }
            if (f.search() != null && !f.search().isBlank()) {
                String pattern = "%" + escapeLike(f.search().trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("transactionReference")), pattern, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("description"), "")), pattern, '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** A user typing "100%" should search for the text "100%", not "100 followed by anything". */
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}