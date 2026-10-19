package com.coretrust.bank.user.service;

import com.coretrust.bank.audit.service.AuditService;
import com.coretrust.bank.auth.repository.RefreshTokenRepository;
import com.coretrust.bank.common.exception.BusinessException;
import com.coretrust.bank.common.exception.ErrorCode;
import com.coretrust.bank.common.exception.ResourceNotFoundException;
import com.coretrust.bank.security.SecurityUser;
import com.coretrust.bank.security.SecurityUserCacheEvictor;
import com.coretrust.bank.user.entity.Permission;
import com.coretrust.bank.user.entity.Role;
import com.coretrust.bank.user.entity.User;
import com.coretrust.bank.user.entity.UserStatus;
import com.coretrust.bank.user.event.UserLockedEvent;
import com.coretrust.bank.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAdminServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private AuditService auditService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private SecurityUserCacheEvictor cacheEvictor;

    @InjectMocks
    private UserAdminService userAdminService;

    private static final SecurityUser ADMIN = actor(1L, Role.ADMIN, Set.of());
    private static final SecurityUser SUPPORT_MANAGER = actor(2L, Role.SUPPORT, Set.of(Permission.MANAGE_USERS));
    private static final SecurityUser PLAIN_SUPPORT = actor(3L, Role.SUPPORT, Set.of());

    @Test
    void supportWithManageUsers_canLockACustomer_whichEndsTheirSession() {
        User customer = user(10L, Role.CUSTOMER);
        customer.startSession("s1", Instant.now().plusSeconds(600));
        when(userRepository.findById(10L)).thenReturn(Optional.of(customer));

        userAdminService.lock(SUPPORT_MANAGER, 10L, "Suspected compromise", null);

        assertThat(customer.getStatus()).isEqualTo(UserStatus.LOCKED);
        assertThat(customer.getActiveSessionId()).isNull();
        verify(refreshTokenRepository).revokeAllActiveByUserId(10L);
        verify(eventPublisher).publishEvent(any(UserLockedEvent.class));
    }

    @Test
    void nobodyCanLockThemselves() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, Role.ADMIN)));

        assertThatThrownBy(() -> userAdminService.lock(ADMIN, 1L, "test", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void supportCannotLockStaff_evenWithManageUsers() {
        when(userRepository.findById(5L)).thenReturn(Optional.of(user(5L, Role.ADMIN)));

        assertThatThrownBy(() -> userAdminService.lock(SUPPORT_MANAGER, 5L, "test", null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void plainSupport_cannotEvenSeeStaffUsers() {
        when(userRepository.findById(5L)).thenReturn(Optional.of(user(5L, Role.ADMIN)));

        assertThatThrownBy(() -> userAdminService.get(PLAIN_SUPPORT, 5L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void permissions_canOnlyBeGrantedToSupportUsers() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, Role.CUSTOMER)));

        assertThatThrownBy(() -> userAdminService.updatePermissions(ADMIN, 10L, Set.of(Permission.VIEW_AUDIT_LOGS)))
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void grantingPermissions_evictsTheSecurityCache() {
        when(userRepository.findById(3L)).thenReturn(Optional.of(user(3L, Role.SUPPORT)));

        userAdminService.updatePermissions(ADMIN, 3L, Set.of(Permission.VIEW_AUDIT_LOGS));

        verify(cacheEvictor).evictAfterCommit(3L);   // collection changes don't fire @PostUpdate
    }

    @Test
    void thePrimaryAdmin_canNeverBeLocked() {
        ReflectionTestUtils.setField(userAdminService, "primaryAdminEmail", "admin@coretrust.com");
        User primaryAdmin = user(1L, Role.ADMIN);
        primaryAdmin.setEmail("admin@coretrust.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(primaryAdmin));

        assertThatThrownBy(() -> userAdminService.lock(actor(9L, Role.ADMIN, Set.of()), 1L, "test", null))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST_STATE);
        assertThat(primaryAdmin.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    // ---------- helpers ----------

    private static SecurityUser actor(Long id, Role role, Set<Permission> permissions) {
        return new SecurityUser(id, role.name().toLowerCase() + "@test.com", null, role,
                UserStatus.ACTIVE, "session", permissions);
    }

    private static User user(Long id, Role role) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setName("User " + id);
        user.setEmail("user" + id + "@test.com");
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}