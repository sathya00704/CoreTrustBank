package com.coretrust.bank.account.repository;

import com.coretrust.bank.account.entity.Account;
import com.coretrust.bank.account.entity.AccountStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/** Builds a WHERE clause from whichever optional filters were provided. */
public final class AccountSpecifications {

    private AccountSpecifications() {
    }

    public static Specification<Account> withFilters(Long userId, AccountStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (userId != null) {
                predicates.add(cb.equal(root.get("user").get("id"), userId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}