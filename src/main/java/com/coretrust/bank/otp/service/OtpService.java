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
import com.coretrust.bank.ratelimit.RateLimiter;
import com.coretrust.bank.user.entity.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Instant;

@Service
public class OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpRepository otpRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AuditService auditService;
    private final OtpProperties properties;
    private final TransactionTemplate requiresNewTransaction;
    private final RateLimiter rateLimiter;

    public OtpService(OtpRepository otpRepository, PasswordEncoder passwordEncoder, EmailService emailService,
                      AuditService auditService, OtpProperties properties,
                      PlatformTransactionManager transactionManager, RateLimiter rateLimiter) {
        this.otpRepository = otpRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.auditService = auditService;
        this.properties = properties;
        this.requiresNewTransaction = new TransactionTemplate(transactionManager);
        this.requiresNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.rateLimiter = rateLimiter;
    }

    /** Sends the OTP to the customer's registered email. */
    @Transactional
    public OtpChallengeResponse issue(User customer, OtpPurpose purpose, String operationDetails,
                                      String operationSummary, Long requestedBy) {
        return issue(customer, purpose, operationDetails, operationSummary, requestedBy, customer.getEmail());
    }

    /** Sends the OTP to {@code deliverTo}, e.g. a new email address that must be proven first. */
    @Transactional
    public OtpChallengeResponse issue(User customer, OtpPurpose purpose, String operationDetails,
                                      String operationSummary, Long requestedBy, String deliverTo) {
        rateLimiter.consume("otp-issue", customer.getId());
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

        emailService.send(deliverTo, "Your CoreTrust OTP", """
                Dear %s,

                Your OTP is %s. It is valid for %d minutes.

                This OTP was requested to: %s.

                Never share this OTP with anyone. If a CoreTrust staff member is assisting you
                right now, share it only with them. If you did not request this, contact CoreTrust immediately.
                """.formatted(customer.getName(), code, properties.ttl().toMinutes(), operationSummary));

        auditService.record(AuditAction.OTP_ISSUED, AuditEntityType.OTP, otp.getId(),
                "OTP for %s issued to %s".formatted(purpose, deliverTo));

        return new OtpChallengeResponse(otp.getId(), MaskingUtils.maskEmail(deliverTo), otp.getExpiresAt());
    }

    /**
     * Joins the caller's transaction: the OTP is consumed only if the protected operation commits.
     * A wrong attempt is recorded in its own transaction, so it counts even though the caller rolls back.
     */
    @Transactional
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
        if (!otp.getOperationHash().equals(HashUtils.sha256Hex(operationDetails))
                || !passwordEncoder.matches(code, otp.getCodeHash())) {
            boolean codeMatches = passwordEncoder.matches(code, otp.getCodeHash());
            boolean detailsMatch = otp.getOperationHash().equals(HashUtils.sha256Hex(operationDetails));

            if (!codeMatches || !detailsMatch) {
                String auditReason = !codeMatches
                        ? "incorrect code"
                        : "correct code but operation details differ from what the customer approved";
                int remaining = recordFailedAttempt(otpId, purpose, auditReason);

                String message = !codeMatches
                        ? "Incorrect OTP. %d attempt(s) left.".formatted(remaining)
                        : ("These details don't match what the customer approved (for example, the amount or "
                        + "accounts changed). %d attempt(s) left. Request a new OTP if the details have changed.")
                        .formatted(remaining);
                throw new BusinessException(ErrorCode.OTP_INVALID, message);
            }
        }

        otp.markConsumed();   // committed together with the operation, or rolled back with it
        auditService.record(AuditAction.OTP_VERIFIED, AuditEntityType.OTP, otpId,
                "OTP for %s verified".formatted(purpose));
    }

    private int recordFailedAttempt(Long otpId, OtpPurpose purpose, String reason) {
        Integer remaining = requiresNewTransaction.execute(status -> {
            Otp fresh = otpRepository.findById(otpId).orElseThrow();
            fresh.registerFailedAttempt();
            auditService.record(AuditAction.OTP_FAILED, AuditEntityType.OTP, otpId,
                    "OTP check failed for %s: %s".formatted(purpose, reason));
            return fresh.remainingAttempts();
        });
        return remaining == null ? 0 : remaining;
    }
}