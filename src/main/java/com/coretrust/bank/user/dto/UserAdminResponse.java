package com.coretrust.bank.user.dto;

import com.coretrust.bank.user.entity.Permission;
import com.coretrust.bank.user.entity.Role;
import com.coretrust.bank.user.entity.User;
import com.coretrust.bank.user.entity.UserStatus;

import java.time.Instant;
import java.util.Set;

/** The staff view of a user: includes status details the user's own profile doesn't need. */
public record UserAdminResponse(
        Long id,
        String name,
        String email,
        String phone,
        Role role,
        UserStatus status,
        Set<Permission> permissions,
        boolean loggedIn,
        Instant createdAt
) {
    public static UserAdminResponse from(User user) {
        return new UserAdminResponse(user.getId(), user.getName(), user.getEmail(), user.getPhone(),
                user.getRole(), user.getStatus(), Set.copyOf(user.getPermissions()),
                user.hasActiveSession(Instant.now()), user.getCreatedAt());
    }
}