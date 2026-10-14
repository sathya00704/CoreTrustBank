package com.coretrust.bank.security;

import com.coretrust.bank.user.entity.Role;
import com.coretrust.bank.user.entity.User;
import com.coretrust.bank.user.entity.UserStatus;
import com.coretrust.bank.user.entity.Permission;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * The logged-in user as Spring Security sees it.
 * A small immutable copy of the User entity, never the entity itself.
 */
public record SecurityUser(Long id, String email, String passwordHash, Role role, UserStatus status,
                           String activeSessionId, Set<Permission> permissions)
        implements UserDetails {

    public static SecurityUser from(User user) {
        return new SecurityUser(user.getId(), user.getEmail(), user.getPasswordHash(),
                user.getRole(), user.getStatus(), user.getActiveSessionId(),
                Set.copyOf(user.getPermissions()));
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
        for (Permission permission : effectivePermissions()) {
            authorities.add(new SimpleGrantedAuthority(permission.authority()));
        }
        return authorities;
    }

    /** Admins implicitly hold every permission. */
    public Set<Permission> effectivePermissions() {
        return role == Role.ADMIN ? EnumSet.allOf(Permission.class) : permissions;
    }

    public boolean hasPermission(Permission permission) {
        return effectivePermissions().contains(permission);
    }

    /** ADMIN and SUPPORT can see all customers' data; CUSTOMERs only their own. */
    public boolean isStaff() {
        return role == Role.ADMIN || role == Role.SUPPORT;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return status != UserStatus.LOCKED;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return status == UserStatus.ACTIVE;
    }

    /** For caching: the password hash never leaves the database. */
    public SecurityUser withoutPassword() {
        return new SecurityUser(id, email, null, role, status, activeSessionId, permissions);
    }
}