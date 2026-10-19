package com.coretrust.bank.user.service;

import com.coretrust.bank.common.exception.BusinessException;
import com.coretrust.bank.common.exception.ErrorCode;
import com.coretrust.bank.common.exception.ResourceNotFoundException;
import com.coretrust.bank.user.dto.UpdateProfileRequest;
import com.coretrust.bank.user.dto.UserResponse;
import com.coretrust.bank.user.entity.User;
import com.coretrust.bank.user.entity.Role;
import com.coretrust.bank.user.entity.UserStatus;
import com.coretrust.bank.user.repository.UserRepository;
import com.coretrust.bank.auth.repository.RefreshTokenRepository;
import com.coretrust.bank.user.dto.ChangePasswordRequest;
import com.coretrust.bank.audit.entity.AuditAction;
import com.coretrust.bank.audit.entity.AuditEntityType;
import com.coretrust.bank.audit.service.AuditService;
import com.coretrust.bank.common.util.MaskingUtils;
import com.coretrust.bank.otp.dto.OtpChallengeResponse;
import com.coretrust.bank.otp.entity.OtpPurpose;
import com.coretrust.bank.otp.service.OtpService;
import com.coretrust.bank.security.SecurityUser;
import com.coretrust.bank.user.dto.PhoneUpdateOtpRequest;
import com.coretrust.bank.user.dto.PhoneUpdateRequest;
import com.coretrust.bank.user.dto.UserAdminResponse;
import com.coretrust.bank.user.event.PhoneUpdatedEvent;
import com.coretrust.bank.user.dto.EmailChangeOtpRequest;
import com.coretrust.bank.user.dto.EmailChangeRequest;
import com.coretrust.bank.user.event.EmailChangedEvent;
import org.springframework.context.ApplicationEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;

    private final AuditService auditService;

    private final OtpService otpService;
    private final ApplicationEventPublisher eventPublisher;

    private static final String UPDATE_PHONE_OPERATION = "UPDATE_PHONE|customer=%d|phone=%s";

    private static final String CHANGE_EMAIL_OPERATION = "CHANGE_EMAIL|user=%d|email=%s";

    @Transactional(readOnly = true)
    public UserResponse getProfile(Long userId) {
        return UserResponse.from(findUser(userId));
    }

    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = findUser(userId);

        if (request.name() != null) {
            user.setName(request.name().trim());
        }
        if (request.phone() != null && !request.phone().equals(user.getPhone())) {
            if (userRepository.existsByPhone(request.phone())) {
                throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "Phone number is already registered.");
            }
            String oldMasked = MaskingUtils.maskPhone(user.getPhone());
            user.setPhone(request.phone());
            // If someone else is using this session, the real owner finds out from the alert
            eventPublisher.publishEvent(new PhoneUpdatedEvent(user.getName(), user.getEmail(),
                    oldMasked, MaskingUtils.maskPhone(request.phone()), "you"));
        }

        auditService.record(AuditAction.PROFILE_UPDATED, AuditEntityType.USER, userId, "Profile updated");

        // No save() call needed: dirty checking writes the changes when the transaction commits
        return UserResponse.from(user);
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = findUser(userId);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Current password is incorrect.");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "New password must be different from the current password.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));

        // Log out every device, including a possibly stolen session
        refreshTokenRepository.revokeAllActiveByUserId(userId);

        user.endSession();   // log out everywhere, including this tab

        auditService.record(AuditAction.PASSWORD_CHANGED, AuditEntityType.USER, userId,
                "Password changed; all sessions revoked");
    }

    /**
     * For staff-assisted operations. Staff emails deliberately return "not found"
     * so the endpoint can't be used to discover staff accounts.
     */
    @Transactional(readOnly = true)
    public User findActiveCustomerByEmail(String email) {
        User user = userRepository.findByEmail(email.trim().toLowerCase())
                .filter(u -> u.getRole() == Role.CUSTOMER)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", email));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED, "Customer account is inactive or locked.");
        }
        return user;
    }

    /** Step 1 (staff): validate the new number, then email the customer an OTP naming it. */
    @Transactional
    public OtpChallengeResponse requestPhoneUpdateOtp(SecurityUser staff, PhoneUpdateOtpRequest request) {
        User customer = findActiveCustomerByEmail(request.customerEmail());
        validateNewPhone(customer, request.newPhone());

        return otpService.issue(customer, OtpPurpose.UPDATE_PHONE,
                UPDATE_PHONE_OPERATION.formatted(customer.getId(), request.newPhone()),
                "change the phone number on your profile to %s (requested by CoreTrust support on your behalf)"
                        .formatted(MaskingUtils.maskPhone(request.newPhone())),
                staff.id());
    }

    /** Step 2 (staff): change the number once the customer has shared the OTP. */
    @Transactional
    public UserAdminResponse updatePhoneAssisted(SecurityUser staff, PhoneUpdateRequest request) {
        User customer = findActiveCustomerByEmail(request.customerEmail());
        validateNewPhone(customer, request.newPhone());
        otpService.verify(request.otpId(), customer.getId(), OtpPurpose.UPDATE_PHONE,
                UPDATE_PHONE_OPERATION.formatted(customer.getId(), request.newPhone()), request.otpCode());

        String oldMasked = MaskingUtils.maskPhone(customer.getPhone());
        String newMasked = MaskingUtils.maskPhone(request.newPhone());
        customer.setPhone(request.newPhone());

        auditService.record(AuditAction.PHONE_UPDATED, AuditEntityType.USER, customer.getId(),
                "Phone of %s changed from %s to %s by %s %s (OTP verified)".formatted(
                        customer.getEmail(), oldMasked, newMasked, staff.role(), staff.email()));
        eventPublisher.publishEvent(new PhoneUpdatedEvent(customer.getName(), customer.getEmail(),
                oldMasked, newMasked, "CoreTrust Support, at your request"));
        return UserAdminResponse.from(customer);
    }

    private void validateNewPhone(User customer, String newPhone) {
        if (newPhone.equals(customer.getPhone())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "This is already the customer's phone number.");
        }
        if (userRepository.existsByPhone(newPhone)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE,
                    "This phone number is already registered to another profile.");
        }
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    /** Step 1: password check, then an OTP to the NEW address (proves the customer owns it). */
    @Transactional
    public OtpChallengeResponse requestEmailChange(SecurityUser principal, EmailChangeOtpRequest request) {
        User user = findUser(principal.id());
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Current password is incorrect.");
        }
        String newEmail = request.newEmail().trim().toLowerCase();
        validateNewEmail(user, newEmail);

        auditService.record(AuditAction.EMAIL_CHANGE_REQUESTED, AuditEntityType.USER, user.getId(),
                "Email change to %s requested".formatted(newEmail));
        return otpService.issue(user, OtpPurpose.CHANGE_EMAIL,
                CHANGE_EMAIL_OPERATION.formatted(user.getId(), newEmail),
                "change the email address of your CoreTrust profile to " + newEmail,
                principal.id(), newEmail);
    }

    /** Step 2: change it. The email is the login identity, so every session ends. */
    @Transactional
    public void changeEmail(SecurityUser principal, EmailChangeRequest request) {
        User user = findUser(principal.id());
        String newEmail = request.newEmail().trim().toLowerCase();
        validateNewEmail(user, newEmail);
        otpService.verify(request.otpId(), user.getId(), OtpPurpose.CHANGE_EMAIL,
                CHANGE_EMAIL_OPERATION.formatted(user.getId(), newEmail), request.otpCode());

        String oldEmail = user.getEmail();
        user.setEmail(newEmail);
        user.endSession();
        refreshTokenRepository.revokeAllActiveByUserId(user.getId());

        auditService.record(AuditAction.EMAIL_CHANGED, AuditEntityType.USER, user.getId(),
                "Email changed from %s to %s; all sessions ended".formatted(oldEmail, newEmail));
        eventPublisher.publishEvent(new EmailChangedEvent(user.getName(), oldEmail, newEmail));
    }

    private void validateNewEmail(User user, String newEmail) {
        if (newEmail.equals(user.getEmail())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "This is already your email address.");
        }
        if (userRepository.existsByEmail(newEmail)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "This email is already registered.");
        }
    }
}