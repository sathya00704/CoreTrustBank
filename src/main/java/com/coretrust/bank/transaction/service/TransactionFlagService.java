package com.coretrust.bank.transaction.service;

import com.coretrust.bank.audit.entity.AuditAction;
import com.coretrust.bank.audit.entity.AuditEntityType;
import com.coretrust.bank.audit.service.AuditService;
import com.coretrust.bank.common.exception.ResourceNotFoundException;
import com.coretrust.bank.security.SecurityUser;
import com.coretrust.bank.transaction.dto.TransactionFlagResponse;
import com.coretrust.bank.transaction.entity.Transaction;
import com.coretrust.bank.transaction.repository.TransactionRepository;
import com.coretrust.bank.user.entity.Role;
import org.springframework.security.access.AccessDeniedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransactionFlagService {

    private final TransactionRepository transactionRepository;
    private final AuditService auditService;
    public static final String SYSTEM_ACTOR = "system@coretrust";

    @Transactional
    public TransactionFlagResponse flag(SecurityUser actor, Long transactionId, String reason) {
        Transaction txn = find(transactionId);
        txn.flag(actor.id(), actor.email(), reason);
        auditService.record(AuditAction.TRANSACTION_FLAGGED, AuditEntityType.TRANSACTION, transactionId,
                "%s (INR %s) flagged as suspicious. Reason: %s".formatted(
                        txn.getTransactionReference(), txn.getAmount().toPlainString(), reason));
        return TransactionFlagResponse.from(txn);
    }

    /** Admins: any flag. Support with FLAG_TRANSACTIONS: only flags they raised themselves. */
    @Transactional
    public TransactionFlagResponse unflag(SecurityUser actor, Long transactionId, String reason) {
        Transaction txn = find(transactionId);
        if (txn.isFlagged() && actor.role() != Role.ADMIN && !actor.id().equals(txn.getFlaggedById())) {
            throw new AccessDeniedException("You can only remove flags you raised yourself.");
        }
        txn.unflag();
        auditService.record(AuditAction.TRANSACTION_UNFLAGGED, AuditEntityType.TRANSACTION, transactionId,
                "%s flag removed. Reason: %s".formatted(txn.getTransactionReference(), reason));
        return TransactionFlagResponse.from(txn);
    }

    private Transaction find(Long transactionId) {
        return transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", transactionId));
    }

    /** Called by the risk monitor. No user: flaggedById stays null, so only admins can remove it. */
    @Transactional
    public void flagBySystem(Long transactionId, String reason) {
        Transaction txn = find(transactionId);
        if (txn.isFlagged()) {
            return;   // already under review: nothing to add
        }
        txn.flag(null, SYSTEM_ACTOR, reason);
        auditService.record(null, SYSTEM_ACTOR, AuditAction.TRANSACTION_FLAGGED, AuditEntityType.TRANSACTION,
                transactionId, "%s auto-flagged: %s".formatted(txn.getTransactionReference(), reason));
    }
}