package com.coretrust.bank.account.service;

import com.coretrust.bank.account.dto.UnfreezeRequestResponse;
import com.coretrust.bank.account.entity.Account;
import com.coretrust.bank.account.entity.AccountStatus;
import com.coretrust.bank.account.entity.UnfreezeRequest;
import com.coretrust.bank.account.entity.UnfreezeRequestStatus;
import com.coretrust.bank.account.repository.AccountRepository;
import com.coretrust.bank.account.repository.UnfreezeRequestRepository;
import com.coretrust.bank.audit.entity.AuditAction;
import com.coretrust.bank.audit.entity.AuditEntityType;
import com.coretrust.bank.audit.service.AuditService;
import com.coretrust.bank.common.dto.PageResponse;
import com.coretrust.bank.common.exception.BusinessException;
import com.coretrust.bank.common.exception.ErrorCode;
import com.coretrust.bank.common.exception.ResourceNotFoundException;
import com.coretrust.bank.otp.dto.OtpChallengeResponse;
import com.coretrust.bank.otp.entity.OtpPurpose;
import com.coretrust.bank.otp.service.OtpService;
import com.coretrust.bank.security.SecurityUser;
import com.coretrust.bank.account.dto.CreateUnfreezeRequest;
import com.coretrust.bank.otp.dto.OtpChallengeResponse;
import com.coretrust.bank.otp.entity.OtpPurpose;
import com.coretrust.bank.otp.service.OtpService;
import com.coretrust.bank.account.event.AccountUnfrozenEvent;
import com.coretrust.bank.user.entity.User;
import org.springframework.context.ApplicationEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UnfreezeRequestService {

    private final UnfreezeRequestRepository unfreezeRequestRepository;
    private final AccountRepository accountRepository;
    private final AuditService auditService;

    private final OtpService otpService;

    private static final String UNFREEZE_REQUEST_OPERATION = "UNFREEZE_REQUEST|account=%d";

    private final ApplicationEventPublisher eventPublisher;

    /** Way 2, step 2 (support): raise the request with the customer's OTP. An admin approves it later. */
    @Transactional
    public UnfreezeRequestResponse create(SecurityUser staff, Long accountId, CreateUnfreezeRequest body) {
        Account account = findRequestableAccount(accountId);
        otpService.verify(body.otpId(), account.getUser().getId(), OtpPurpose.UNFREEZE_ACCOUNT,
                UNFREEZE_REQUEST_OPERATION.formatted(accountId), body.otpCode());

        UnfreezeRequest request = new UnfreezeRequest();
        request.setAccount(account);
        request.setRequestedById(staff.id());
        request.setRequestedByEmail(staff.email());
        request.setReason(body.reason());
        UnfreezeRequest saved = unfreezeRequestRepository.save(request);

        auditService.record(AuditAction.UNFREEZE_REQUESTED, AuditEntityType.UNFREEZE_REQUEST, saved.getId(),
                "Unfreeze requested for account %s (customer OTP verified). Reason: %s"
                        .formatted(account.getAccountNumber(), body.reason()));
        return UnfreezeRequestResponse.from(saved);
    }

    /** Frozen, with no request already pending. Checked before the OTP is sent and again before saving. */
    private Account findRequestableAccount(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        if (account.getStatus() != AccountStatus.FROZEN) {
            throw new BusinessException(ErrorCode.INVALID_ACCOUNT_STATE,
                    "Only FROZEN accounts can have unfreeze requests.");
        }
        if (unfreezeRequestRepository.existsByAccountIdAndStatus(accountId, UnfreezeRequestStatus.PENDING)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE,
                    "A pending unfreeze request already exists for this account.");
        }
        return account;
    }

    @Transactional(readOnly = true)
    public PageResponse<UnfreezeRequestResponse> list(UnfreezeRequestStatus status, Pageable pageable) {
        var page = (status == null)
                ? unfreezeRequestRepository.findAll(pageable)
                : unfreezeRequestRepository.findByStatus(status, pageable);
        return PageResponse.from(page.map(UnfreezeRequestResponse::from));
    }

    /** Checker: admin approves, which unfreezes the account in the same transaction. */
    @Transactional
    public UnfreezeRequestResponse approve(SecurityUser admin, Long requestId, String note) {
        UnfreezeRequest request = findForReview(admin, requestId);
        request.approve(admin.id(), admin.email(), note);
        request.getAccount().unfreeze();   // throws if the account is no longer FROZEN → everything rolls back

        auditService.record(AuditAction.UNFREEZE_APPROVED, AuditEntityType.UNFREEZE_REQUEST, requestId,
                "Unfreeze request approved. Note: " + note);
        auditService.record(AuditAction.ACCOUNT_UNFROZEN, AuditEntityType.ACCOUNT, request.getAccount().getId(),
                "Account %s unfrozen via approved request %d".formatted(request.getAccount().getAccountNumber(), requestId));

        Account account = request.getAccount();
        User owner = account.getUser();
        eventPublisher.publishEvent(new AccountUnfrozenEvent(account.getAccountNumber(),
                owner.getName(), owner.getEmail(),
                "CoreTrust Bank, after reviewing your request raised through Support",
                "Your request was reviewed and approved by CoreTrust."));
        return UnfreezeRequestResponse.from(request);
    }

    @Transactional
    public UnfreezeRequestResponse reject(SecurityUser admin, Long requestId, String note) {
        UnfreezeRequest request = findForReview(admin, requestId);
        request.reject(admin.id(), admin.email(), note);
        auditService.record(AuditAction.UNFREEZE_REJECTED, AuditEntityType.UNFREEZE_REQUEST, requestId,
                "Unfreeze request rejected. Note: " + note);
        return UnfreezeRequestResponse.from(request);
    }

    /** Way 2, step 1 (support): email an OTP to the account owner. */
    @Transactional
    public OtpChallengeResponse requestOtp(SecurityUser staff, Long accountId) {
        Account account = findRequestableAccount(accountId);
        return otpService.issue(account.getUser(), OtpPurpose.UNFREEZE_ACCOUNT,
                UNFREEZE_REQUEST_OPERATION.formatted(accountId),
                "unfreeze your account %s (requested by CoreTrust support on your behalf)"
                        .formatted(account.getAccountNumber()),
                staff.id());
    }

    private UnfreezeRequest findForReview(SecurityUser admin, Long requestId) {
        UnfreezeRequest request = unfreezeRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Unfreeze request", requestId));
        if (request.getRequestedById().equals(admin.id())) {
            throw new BusinessException(ErrorCode.SELF_APPROVAL_NOT_ALLOWED,
                    "You cannot review a request you raised yourself.");
        }
        return request;
    }
}