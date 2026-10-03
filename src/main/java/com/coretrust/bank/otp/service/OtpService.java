package com.coretrust.bank.otp.service;

import com.coretrust.bank.audit.entity.AuditAction;
import com.coretrust.bank.audit.entity.AuditEntityType;
import com.coretrust.bank.audit.service.AuditService;
import com.coretrust.bank.common.exception.BusinessException;
import com.coretrust.bank.common.exception.ErrorCode;
import com.coretrust.bank.common.util.HashUtils;
import com.coretrust.bank.common.util.MaskingUtils;
import com.coretrust.bank.notification.service.EmailService;
import com.coretrust.bank.otp.config.OtpProperties;
import com.coretrust.bank.otp.dto.OtpChallengeResponse;
import com.coretrust.bank.otp.entity.Otp;
import com.coretrust.bank.otp.entity.OtpPurpose;
import com.coretrust.bank.otp.repository.OtpRepository;
import com.coretrust.bank.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpRepository otpRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AuditService auditService;
    private final OtpProperties properties;

    /**
     * @param operationDetails canonical description of the exact operation (hashed and bound to the OTP)
     * @param operationSummary human-readable description shown to the customer in the email
     */
    @Transactional
    public OtpChallengeResponse issue(User customer, OtpPurpose purpose, String operationDetails,
                                      String operationSummary, Long requestedBy) {
        otpRepository.invalidateActive(customer.getId(), purpose);

        String code = "%06d".formatted(RANDOM.nextInt(1_000_000));

        Otp otp = new Otp();
        otp.setUser(customer);
        otp.setPurpose(purpose);
        otp.setCodeHash(passwordEncoder.encode(code));
        otp.setOperationHash(HashUtils.sha256Hex(operationDetails));
        otp.setExpiresAt(Instant.now().plus(properties.ttl()));
        otp.setMaxAttempts(properties.maxAttempts());
        otp.setRequestedBy(requestedBy);
        otpRepository.save(otp);

        emailService.send(customer.getEmail(), "Your CoreTrust OTP", """
                Dear %s,

                Your OTP is %s. It is valid for %d minutes.

                This OTP was requested to: %s.

                Never share this OTP with anyone. If a CoreTrust staff member is assisting you
                right now, share it only with them. If you did not request this, contact CoreTrust immediately.
                """.formatted(customer.getName(), code, properties.ttl().toMinutes(), operationSummary));

        auditService.record(AuditAction.OTP_ISSUED, AuditEntityType.OTP, otp.getId(),
                "OTP for %s issued to customer %s".formatted(purpose, customer.getEmail()));

        return new OtpChallengeResponse(otp.getId(), MaskingUtils.maskEmail(customer.getEmail()), otp.getExpiresAt());
    }

    /**
     * Own transaction: failed attempts and consumption are committed even if the calling operation
     * later rolls back. An OTP is spent once verified, even if the operation itself then fails.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = BusinessException.class)
    public void verify(Long otpId, Long customerId, OtpPurpose purpose, String operationDetails, String code) {
        Otp otp = otpRepository.findById(otpId)
                .orElseThrow(() -> new BusinessException(ErrorCode.OTP_INVALID, "OTP is invalid."));

        if (!otp.getUser().getId().equals(customerId) || otp.getPurpose() != purpose) {
            throw new BusinessException(ErrorCode.OTP_INVALID, "OTP is invalid.");
        }
        if (otp.isConsumed()) {
            throw new BusinessException(ErrorCode.OTP_INVALID, "OTP has already been used or replaced.");
        }
        if (otp.isExpired(Instant.now())) {
            throw new BusinessException(ErrorCode.OTP_EXPIRED, "OTP has expired. Please request a new one.");
        }
        if (!otp.hasAttemptsLeft()) {
            throw new BusinessException(ErrorCode.OTP_ATTEMPTS_EXCEEDED,
                    "Too many incorrect attempts. Please request a new OTP.");
        }
        // Same OTP, different details (e.g. account type changed after sending) = not what the customer approved
        if (!otp.getOperationHash().equals(HashUtils.sha256Hex(operationDetails))
                || !passwordEncoder.matches(code, otp.getCodeHash())) {
            otp.registerFailedAttempt();
            auditService.record(AuditAction.OTP_FAILED, AuditEntityType.OTP, otpId,
                    "Incorrect OTP or changed operation details for %s".formatted(purpose));
            throw new BusinessException(ErrorCode.OTP_INVALID,
                    "Incorrect OTP. %d attempt(s) left.".formatted(otp.remainingAttempts()));
        }

        otp.markConsumed();
        auditService.record(AuditAction.OTP_VERIFIED, AuditEntityType.OTP, otpId,
                "OTP for %s verified".formatted(purpose));
    }
}