package com.coretrust.bank.user.repository;

import com.coretrust.bank.common.util.LikePatterns;
import com.coretrust.bank.user.entity.Role;
import com.coretrust.bank.user.entity.User;
import com.coretrust.bank.user.entity.UserStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class UserSpecifications {

    private UserSpecifications() {
    }

    /** search matches name, email or phone (case-insensitive, partial). */
    public static Specification<User> withFilters(Role role, UserStatus status, String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (role != null) {
                predicates.add(cb.equal(root.get("role"), role));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (search != null && !search.isBlank()) {
                String pattern = LikePatterns.contains(search);
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern, '\\'),
                        cb.like(cb.lower(root.get("email")), pattern, '\\'),
                        cb.like(root.get("phone"), pattern, '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}