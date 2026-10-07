package com.coretrust.bank.dispute.service;

import com.coretrust.bank.audit.entity.AuditAction;
import com.coretrust.bank.audit.entity.AuditEntityType;
import com.coretrust.bank.audit.service.AuditService;
import com.coretrust.bank.common.dto.PageResponse;
import com.coretrust.bank.common.exception.BusinessException;
import com.coretrust.bank.common.exception.ErrorCode;
import com.coretrust.bank.common.exception.ResourceNotFoundException;
import com.coretrust.bank.dispute.dto.DisputeResponse;
import com.coretrust.bank.dispute.dto.RaiseDisputeRequest;
import com.coretrust.bank.dispute.entity.Dispute;
import com.coretrust.bank.dispute.entity.DisputeStatus;
import com.coretrust.bank.dispute.repository.DisputeRepository;
import com.coretrust.bank.security.SecurityUser;
import com.coretrust.bank.transaction.entity.Transaction;
import com.coretrust.bank.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DisputeService {

    private final DisputeRepository disputeRepository;
    private final TransactionRepository transactionRepository;
    private final AuditService auditService;

    @Transactional
    public DisputeResponse raise(SecurityUser staff, Long transactionId, RaiseDisputeRequest request) {
        Transaction txn = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", transactionId));
        if (disputeRepository.existsByTransactionIdAndStatus(transactionId, DisputeStatus.OPEN)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE,
                    "An open dispute already exists for this transaction.");
        }

        Dispute dispute = new Dispute();
        dispute.setTransaction(txn);
        dispute.setCategory(request.category());
        dispute.setDescription(request.description().trim());
        dispute.setRaisedById(staff.id());
        dispute.setRaisedByEmail(staff.email());
        Dispute saved = disputeRepository.save(dispute);

        auditService.record(AuditAction.DISPUTE_RAISED, AuditEntityType.DISPUTE, saved.getId(),
                "%s dispute on %s (INR %s): %s".formatted(request.category(), txn.getTransactionReference(),
                        txn.getAmount().toPlainString(), saved.getDescription()));
        return DisputeResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<DisputeResponse> list(DisputeStatus status, Pageable pageable) {
        var page = (status == null)
                ? disputeRepository.findAll(pageable)
                : disputeRepository.findByStatus(status, pageable);
        return PageResponse.from(page.map(DisputeResponse::from));
    }

    @Transactional
    public DisputeResponse resolve(SecurityUser admin, Long disputeId, String note) {
        Dispute dispute = find(disputeId);
        dispute.resolve(admin.id(), admin.email(), note);
        auditService.record(AuditAction.DISPUTE_RESOLVED, AuditEntityType.DISPUTE, disputeId,
                "Dispute resolved. Note: " + note);
        return DisputeResponse.from(dispute);
    }

    @Transactional
    public DisputeResponse reject(SecurityUser admin, Long disputeId, String note) {
        Dispute dispute = find(disputeId);
        dispute.reject(admin.id(), admin.email(), note);
        auditService.record(AuditAction.DISPUTE_REJECTED, AuditEntityType.DISPUTE, disputeId,
                "Dispute rejected. Note: " + note);
        return DisputeResponse.from(dispute);
    }

    private Dispute find(Long disputeId) {
        return disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("Dispute", disputeId));
    }
}