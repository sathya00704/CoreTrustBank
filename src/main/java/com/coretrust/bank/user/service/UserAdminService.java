package com.coretrust.bank.user.service;

import com.coretrust.bank.audit.entity.AuditAction;
import com.coretrust.bank.audit.entity.AuditEntityType;
import com.coretrust.bank.audit.service.AuditService;
import com.coretrust.bank.auth.repository.RefreshTokenRepository;
import com.coretrust.bank.common.dto.PageResponse;
import com.coretrust.bank.common.exception.BusinessException;
import com.coretrust.bank.common.exception.ErrorCode;
import com.coretrust.bank.common.exception.ResourceNotFoundException;
import com.coretrust.bank.common.util.SortValidator;
import com.coretrust.bank.security.SecurityUser;
import com.coretrust.bank.user.dto.UserAdminResponse;
import com.coretrust.bank.user.entity.Permission;
import com.coretrust.bank.user.entity.Role;
import com.coretrust.bank.user.entity.User;
import com.coretrust.bank.user.entity.UserStatus;
import com.coretrust.bank.user.event.UserLockedEvent;
import com.coretrust.bank.user.event.UserUnlockedEvent;
import com.coretrust.bank.user.repository.UserRepository;
import com.coretrust.bank.user.repository.UserSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserAdminService {

    private static final Set<String> SORTABLE = Set.of("createdAt", "name", "email");

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;

    /** Plain SUPPORT sees customers only; MANAGE_USERS (and admins) see everyone. */
    @Transactional(readOnly = true)
    public PageResponse<UserAdminResponse> list(SecurityUser viewer, Role role, UserStatus status,
                                                String search, Pageable pageable) {
        SortValidator.requireAllowed(pageable.getSort(), SORTABLE);
        boolean canSeeStaff = viewer.hasPermission(Permission.MANAGE_USERS);
        if (!canSeeStaff && role != null && role != Role.CUSTOMER) {
            throw new AccessDeniedException("You can only view customers.");
        }
        Role effectiveRole = canSeeStaff ? role : Role.CUSTOMER;
        var page = userRepository.findAll(UserSpecifications.withFilters(effectiveRole, status, search), pageable);
        return PageResponse.from(page.map(UserAdminResponse::from));
    }

    @Transactional(readOnly = true)
    public UserAdminResponse get(SecurityUser viewer, Long userId) {
        return UserAdminResponse.from(findVisible(viewer, userId));
    }

    @Transactional
    public UserAdminResponse lock(SecurityUser actor, Long userId, String reason, String customerMessage) {
        User target = findManageable(actor, userId);
        target.lock();                                           // also ends the active session
        refreshTokenRepository.revokeAllActiveByUserId(target.getId());

        auditService.record(AuditAction.USER_LOCKED, AuditEntityType.USER, target.getId(),
                "%s %s locked by %s %s. Reason: %s".formatted(target.getRole(), target.getEmail(),
                        actor.role(), actor.email(), reason));
        String shownToUser = (customerMessage == null || customerMessage.isBlank())
                ? "For security reasons. Please contact CoreTrust Support for more information."
                : customerMessage.trim();
        eventPublisher.publishEvent(new UserLockedEvent(target.getName(), target.getEmail(), shownToUser));
        return UserAdminResponse.from(target);
    }

    @Transactional
    public UserAdminResponse unlock(SecurityUser actor, Long userId, String reason) {
        User target = findManageable(actor, userId);
        target.unlock();

        auditService.record(AuditAction.USER_UNLOCKED, AuditEntityType.USER, target.getId(),
                "%s %s unlocked by %s %s. Reason: %s".formatted(target.getRole(), target.getEmail(),
                        actor.role(), actor.email(), reason));
        eventPublisher.publishEvent(new UserUnlockedEvent(target.getName(), target.getEmail()));
        return UserAdminResponse.from(target);
    }

    /** Admin only (enforced by the controller and the /admin/** URL rule). Only SUPPORT users hold permissions. */
    @Transactional
    public UserAdminResponse updatePermissions(SecurityUser admin, Long userId, Set<Permission> permissions) {
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        if (target.getRole() != Role.SUPPORT) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Permissions can only be granted to SUPPORT users. Admins already have all of them.");
        }

        Set<Permission> before = target.getPermissions().isEmpty()
                ? EnumSet.noneOf(Permission.class) : EnumSet.copyOf(target.getPermissions());
        target.getPermissions().clear();
        target.getPermissions().addAll(permissions);

        auditService.record(AuditAction.PERMISSIONS_CHANGED, AuditEntityType.USER, target.getId(),
                "Permissions of %s changed from %s to %s by admin %s".formatted(target.getEmail(),
                        before, permissions, admin.email()));
        return UserAdminResponse.from(target);
    }

    // ---------- helpers ----------

    /** Staff lookup; plain SUPPORT may only see customers (others look "not found"). */
    private User findVisible(SecurityUser viewer, Long userId) {
        return userRepository.findById(userId)
                .filter(u -> viewer.hasPermission(Permission.MANAGE_USERS) || u.getRole() == Role.CUSTOMER)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    /** Lock/unlock rules: never yourself; only admins may act on staff users. */
    private User findManageable(SecurityUser actor, Long userId) {
        User target = findVisible(actor, userId);
        if (target.getId().equals(actor.id())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "You cannot lock or unlock yourself.");
        }
        if (target.getRole() != Role.CUSTOMER && actor.role() != Role.ADMIN) {
            throw new AccessDeniedException("Only admins can lock or unlock staff users.");
        }
        return target;
    }
}