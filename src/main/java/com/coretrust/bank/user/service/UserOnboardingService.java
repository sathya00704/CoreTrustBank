package com.coretrust.bank.user.service;

import com.coretrust.bank.account.dto.AccountResponse;
import com.coretrust.bank.account.service.AccountService;
import com.coretrust.bank.audit.entity.AuditAction;
import com.coretrust.bank.audit.entity.AuditEntityType;
import com.coretrust.bank.audit.service.AuditService;
import com.coretrust.bank.auth.service.PasswordSetupService;
import com.coretrust.bank.common.exception.BusinessException;
import com.coretrust.bank.common.exception.ErrorCode;
import com.coretrust.bank.common.exception.ResourceNotFoundException;
import com.coretrust.bank.common.util.MaskingUtils;
import com.coretrust.bank.common.util.SecureTokens;
import com.coretrust.bank.security.SecurityUser;
import com.coretrust.bank.user.dto.AssistedRegistrationRequest;
import com.coretrust.bank.user.dto.AssistedRegistrationResponse;
import com.coretrust.bank.user.dto.CreateStaffRequest;
import com.coretrust.bank.user.dto.UserAdminResponse;
import com.coretrust.bank.user.entity.Permission;
import com.coretrust.bank.user.entity.Role;
import com.coretrust.bank.user.entity.User;
import com.coretrust.bank.user.entity.UserStatus;
import com.coretrust.bank.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserOnboardingService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordSetupService passwordSetupService;
    private final AccountService accountService;
    private final AuditService auditService;

    /** Admin creates a SUPPORT or ADMIN user. */
    @Transactional
    public UserAdminResponse createStaff(SecurityUser admin, CreateStaffRequest request) {
        if (request.role() == Role.CUSTOMER) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Customers are registered through assisted registration, not as staff.");
        }
        User user = createPendingUser(request.name(), request.email(), request.phone(), request.role());
        passwordSetupService.sendSetupLink(user);

        auditService.record(AuditAction.STAFF_USER_CREATED, AuditEntityType.USER, user.getId(),
                "%s user %s created by admin %s".formatted(user.getRole(), user.getEmail(), admin.email()));
        return UserAdminResponse.from(user);
    }

    /** Support registers a walk-in customer, optionally opening their first account. */
    @Transactional
    public AssistedRegistrationResponse registerCustomer(SecurityUser staff, AssistedRegistrationRequest request) {
        User customer = createPendingUser(request.name(), request.email(), request.phone(), Role.CUSTOMER);

        AccountResponse account = request.accountType() == null
                ? null
                : accountService.openAccountForNewCustomer(staff, customer.getId(), request.accountType());

        passwordSetupService.sendSetupLink(customer);
        auditService.record(AuditAction.CUSTOMER_REGISTERED_BY_STAFF, AuditEntityType.USER, customer.getId(),
                "Customer %s registered by %s %s".formatted(customer.getEmail(), staff.role(), staff.email()));

        return new AssistedRegistrationResponse(UserAdminResponse.from(customer), account,
                MaskingUtils.maskEmail(customer.getEmail()));
    }

    /** For links that expired or got lost. Plain SUPPORT may resend only to customers. */
    @Transactional
    public String resendSetupLink(SecurityUser staff, Long userId) {
        User user = userRepository.findById(userId)
                .filter(u -> staff.hasPermission(Permission.MANAGE_USERS) || u.getRole() == Role.CUSTOMER)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        if (user.getStatus() != UserStatus.INACTIVE) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST_STATE, "This user has already set a password.");
        }
        passwordSetupService.sendSetupLink(user);
        return MaskingUtils.maskEmail(user.getEmail());
    }

    // ---------- helpers ----------

    private User createPendingUser(String name, String email, String phone, Role role) {
        String normalizedEmail = email.trim().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "Email is already registered.");
        }
        if (userRepository.existsByPhone(phone)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "Phone number is already registered.");
        }

        User user = new User();
        user.setName(name.trim());
        user.setEmail(normalizedEmail);
        user.setPhone(phone);
        // Random, never revealed: nobody can log in with it, and the account is INACTIVE anyway
        user.setPasswordHash(passwordEncoder.encode(SecureTokens.urlSafe(32)));
        user.setRole(role);
        user.setStatus(UserStatus.INACTIVE);
        return userRepository.save(user);
    }
}