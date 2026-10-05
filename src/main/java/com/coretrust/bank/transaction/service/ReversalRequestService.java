package com.coretrust.bank.transaction.service;

import com.coretrust.bank.audit.entity.AuditAction;
import com.coretrust.bank.audit.entity.AuditEntityType;
import com.coretrust.bank.audit.service.AuditService;
import com.coretrust.bank.common.dto.PageResponse;
import com.coretrust.bank.common.exception.BusinessException;
import com.coretrust.bank.common.exception.ErrorCode;
import com.coretrust.bank.common.exception.ResourceNotFoundException;
import com.coretrust.bank.security.SecurityUser;
import com.coretrust.bank.transaction.dto.ReversalRequestResponse;
import com.coretrust.bank.transaction.entity.ReversalRequest;
import com.coretrust.bank.transaction.entity.ReversalRequestStatus;
import com.coretrust.bank.transaction.entity.Transaction;
import com.coretrust.bank.transaction.entity.TransactionStatus;
import com.coretrust.bank.transaction.entity.TransactionType;
import com.coretrust.bank.transaction.repository.ReversalRequestRepository;
import com.coretrust.bank.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ReversalRequestService {

    private static final Set<TransactionType> REVERSIBLE =
            EnumSet.of(TransactionType.TRANSFER, TransactionType.DEPOSIT, TransactionType.WITHDRAWAL);

    private final ReversalRequestRepository reversalRequestRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerService ledgerService;
    private final AuditService auditService;

    /** Maker: support raises the request. Validated now so admins only see requests that make sense. */
    @Transactional
    public ReversalRequestResponse create(SecurityUser staff, Long transactionId, String reason) {
        Transaction txn = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", transactionId));

        if (!REVERSIBLE.contains(txn.getType()) || txn.getStatus() != TransactionStatus.SUCCESS) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST_STATE,
                    "Only successful transfers, deposits and withdrawals can be reversed.");
        }
        if (reversalRequestRepository.existsByTransactionIdAndStatus(transactionId, ReversalRequestStatus.PENDING)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE,
                    "A pending reversal request already exists for this transaction.");
        }

        ReversalRequest request = new ReversalRequest();
        request.setTransaction(txn);
        request.setRequestedById(staff.id());
        request.setRequestedByEmail(staff.email());
        request.setReason(reason);
        ReversalRequest saved = reversalRequestRepository.save(request);

        auditService.record(AuditAction.REVERSAL_REQUESTED, AuditEntityType.REVERSAL_REQUEST, saved.getId(),
                "Reversal requested for %s (%s, INR %s). Reason: %s".formatted(txn.getTransactionReference(),
                        txn.getType(), txn.getAmount().toPlainString(), reason));
        return ReversalRequestResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReversalRequestResponse> list(ReversalRequestStatus status, Pageable pageable) {
        var page = (status == null)
                ? reversalRequestRepository.findAll(pageable)
                : reversalRequestRepository.findByStatus(status, pageable);
        return PageResponse.from(page.map(ReversalRequestResponse::from));
    }

    /** Checker: admin approves, and the money moves back in the same transaction. */
    @Transactional
    public ReversalRequestResponse approve(SecurityUser admin, Long requestId, String note) {
        ReversalRequest request = findForReview(admin, requestId);
        Transaction original = request.getTransaction();

        // Throws (e.g. INSUFFICIENT_FUNDS) → everything rolls back and the request stays PENDING
        Transaction reversal = ledgerService.reverse(original, admin.id());
        request.approve(admin.id(), admin.email(), note, reversal);

        auditService.record(AuditAction.REVERSAL_APPROVED, AuditEntityType.REVERSAL_REQUEST, requestId,
                "Reversal approved. Note: " + note);
        auditService.record(AuditAction.MONEY_REVERSED, AuditEntityType.TRANSACTION, reversal.getId(),
                "%s reverses %s (INR %s)".formatted(reversal.getTransactionReference(),
                        original.getTransactionReference(), original.getAmount().toPlainString()));
        return ReversalRequestResponse.from(request);
    }

    @Transactional
    public ReversalRequestResponse reject(SecurityUser admin, Long requestId, String note) {
        ReversalRequest request = findForReview(admin, requestId);
        request.reject(admin.id(), admin.email(), note);
        auditService.record(AuditAction.REVERSAL_REJECTED, AuditEntityType.REVERSAL_REQUEST, requestId,
                "Reversal rejected. Note: " + note);
        return ReversalRequestResponse.from(request);
    }

    private ReversalRequest findForReview(SecurityUser admin, Long requestId) {
        ReversalRequest request = reversalRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Reversal request", requestId));
        if (request.getRequestedById().equals(admin.id())) {
            throw new BusinessException(ErrorCode.SELF_APPROVAL_NOT_ALLOWED,
                    "You cannot review a request you raised yourself.");
        }
        return request;
    }
}