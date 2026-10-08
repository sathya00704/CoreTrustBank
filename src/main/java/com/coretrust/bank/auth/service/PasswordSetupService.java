package com.coretrust.bank.auth.service;

import com.coretrust.bank.audit.entity.AuditAction;
import com.coretrust.bank.audit.entity.AuditEntityType;
import com.coretrust.bank.audit.service.AuditService;
import com.coretrust.bank.auth.dto.SetPasswordRequest;
import com.coretrust.bank.auth.entity.PasswordSetupToken;
import com.coretrust.bank.auth.entity.PasswordTokenPurpose;
import com.coretrust.bank.auth.repository.PasswordSetupTokenRepository;
import com.coretrust.bank.common.exception.BusinessException;
import com.coretrust.bank.common.exception.ErrorCode;
import com.coretrust.bank.common.util.HashUtils;
import com.coretrust.bank.common.util.SecureTokens;
import com.coretrust.bank.notification.service.EmailService;
import com.coretrust.bank.user.entity.User;
import com.coretrust.bank.user.entity.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class PasswordSetupService {

    private final PasswordSetupTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AuditService auditService;

    @Value("${app.password-setup.ttl}")
    private Duration ttl;

    @Value("${app.frontend.set-password-url}")
    private String setPasswordUrl;

    /**
     * Joins the caller's transaction: if the email fails, the user creation rolls back too,
     * so there's never a user who can't get in. Staff can resend later if a link expires.
     */
    @Transactional
    public void sendSetupLink(User user) {
        tokenRepository.deleteUnused(user.getId(), PasswordTokenPurpose.ACCOUNT_SETUP);

        String rawToken = SecureTokens.urlSafe(32);
        PasswordSetupToken token = new PasswordSetupToken();
        token.setUser(user);
        token.setTokenHash(HashUtils.sha256Hex(rawToken));
        token.setPurpose(PasswordTokenPurpose.ACCOUNT_SETUP);
        token.setExpiresAt(Instant.now().plus(ttl));
        tokenRepository.save(token);

        emailService.send(user.getEmail(), "Set up your CoreTrust password", """
                Dear %s,

                A CoreTrust profile has been created for you. To activate it, choose your password here:

                %s?token=%s

                This link is valid for %d hours and can be used once.
                CoreTrust staff will never ask you for your password.

                If you were not expecting this, you can ignore this email.
                """.formatted(user.getName(), setPasswordUrl, rawToken, ttl.toHours()));

        auditService.record(AuditAction.SETUP_LINK_SENT, AuditEntityType.USER, user.getId(),
                "Password setup link sent to " + user.getEmail());
    }

    @Transactional
    public void setPassword(SetPasswordRequest request) {
        PasswordSetupToken token = tokenRepository.findByTokenHash(HashUtils.sha256Hex(request.token()))
                .filter(t -> t.isUsable(Instant.now()))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN,
                        "This link is invalid or has expired. Ask CoreTrust to send a new one."));

        User user = token.getUser();
        if (user.getStatus() != UserStatus.INACTIVE) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN, "This profile has already been set up.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setStatus(UserStatus.ACTIVE);
        token.markUsed();

        auditService.record(user.getId(), user.getEmail(), AuditAction.PASSWORD_SET, AuditEntityType.USER,
                user.getId(), "Password set via setup link; profile activated");
    }
}