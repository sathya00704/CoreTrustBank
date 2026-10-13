package com.coretrust.bank.caseupdate.service;

import com.coretrust.bank.account.repository.UnfreezeRequestRepository;
import com.coretrust.bank.audit.entity.AuditAction;
import com.coretrust.bank.audit.entity.AuditEntityType;
import com.coretrust.bank.audit.service.AuditService;
import com.coretrust.bank.caseupdate.dto.CaseType;
import com.coretrust.bank.caseupdate.dto.CaseUpdateRequest;
import com.coretrust.bank.caseupdate.event.CaseUpdateEvent;
import com.coretrust.bank.common.exception.BusinessException;
import com.coretrust.bank.common.exception.ErrorCode;
import com.coretrust.bank.common.util.MaskingUtils;
import com.coretrust.bank.dispute.repository.DisputeRepository;
import com.coretrust.bank.security.SecurityUser;
import com.coretrust.bank.transaction.entity.Transaction;
import com.coretrust.bank.transaction.repository.ReversalRequestRepository;
import com.coretrust.bank.user.entity.User;
import com.coretrust.bank.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CaseUpdateService {

    private final UserService userService;
    private final DisputeRepository disputeRepository;
    private final ReversalRequestRepository reversalRequestRepository;
    private final UnfreezeRequestRepository unfreezeRequestRepository;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;

    /** Returns the masked email the update was sent to. */
    @Transactional
    public String send(SecurityUser staff, CaseUpdateRequest request) {
        User customer = userService.findActiveCustomerByEmail(request.customerEmail());
        String title = switch (request.caseType()) {
            case GENERAL -> "Message from CoreTrust Support";
            case DISPUTE -> {
                requireParty(customer, request, disputeRepository.findById(requireCaseId(request))
                        .map(d -> isParty(d.getTransaction(), customer)).orElse(false));
                yield "Update on your dispute #" + request.caseId();
            }
            case REVERSAL_REQUEST -> {
                requireParty(customer, request, reversalRequestRepository.findById(requireCaseId(request))
                        .map(r -> isParty(r.getTransaction(), customer)).orElse(false));
                yield "Update on your reversal request #" + request.caseId();
            }
            case UNFREEZE_REQUEST -> {
                requireParty(customer, request, unfreezeRequestRepository.findById(requireCaseId(request))
                        .map(u -> u.getAccount().getUser().getId().equals(customer.getId())).orElse(false));
                yield "Update on your unfreeze request #" + request.caseId();
            }
        };

        String message = request.message().trim();
        auditService.record(AuditAction.CASE_UPDATE_SENT, AuditEntityType.USER, customer.getId(),
                "%s sent to %s by %s %s: %s".formatted(title, customer.getEmail(), staff.role(), staff.email(), message));
        eventPublisher.publishEvent(new CaseUpdateEvent(customer.getName(), customer.getEmail(), title, message));
        return MaskingUtils.maskEmail(customer.getEmail());
    }

    private static Long requireCaseId(CaseUpdateRequest request) {
        if (request.caseId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "caseId is required for " + request.caseType() + ".");
        }
        return request.caseId();
    }

    /** A case that doesn't exist and a case that isn't this customer's look the same. */
    private static void requireParty(User customer, CaseUpdateRequest request, boolean isParty) {
        if (!isParty) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                    "No %s #%d found for this customer.".formatted(request.caseType(), request.caseId()));
        }
    }

    private static boolean isParty(Transaction t, User customer) {
        return (t.getFromAccount() != null && t.getFromAccount().getUser().getId().equals(customer.getId()))
                || (t.getToAccount() != null && t.getToAccount().getUser().getId().equals(customer.getId()));
    }
}