package com.coretrust.bank.transfer.service;

import com.coretrust.bank.account.entity.Account;
import com.coretrust.bank.account.repository.AccountRepository;
import com.coretrust.bank.audit.entity.AuditAction;
import com.coretrust.bank.audit.entity.AuditEntityType;
import com.coretrust.bank.audit.service.AuditService;
import com.coretrust.bank.beneficiary.entity.BeneficiaryStatus;
import com.coretrust.bank.beneficiary.repository.BeneficiaryRepository;
import com.coretrust.bank.common.exception.BusinessException;
import com.coretrust.bank.common.exception.ErrorCode;
import com.coretrust.bank.common.exception.ResourceNotFoundException;
import com.coretrust.bank.idempotency.dto.IdempotentResult;
import com.coretrust.bank.idempotency.service.IdempotencyService;
import com.coretrust.bank.security.SecurityUser;
import com.coretrust.bank.transaction.entity.Transaction;
import com.coretrust.bank.transaction.entity.TransactionType;
import com.coretrust.bank.transaction.repository.TransactionRepository;
import com.coretrust.bank.transaction.service.LedgerService;
import com.coretrust.bank.transfer.dto.TransferRequest;
import com.coretrust.bank.transfer.dto.TransferResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransferService {

    private final IdempotencyService idempotencyService;
    private final LedgerService ledgerService;
    private final AccountRepository accountRepository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final TransactionRepository transactionRepository;
    private final AuditService auditService;

    @Value("${app.bank.ifsc}")
    private String bankIfsc;

    /** Not @Transactional: IdempotencyService opens one transaction per attempt. */
    public IdempotentResult<TransferResponse> transfer(SecurityUser principal, String idempotencyKey,
                                                       TransferRequest request) {
        String fingerprint = "from=%d|to=%s|amount=%s|desc=%s".formatted(
                request.fromAccountId(), request.toAccountNumber().trim(),
                request.amount().stripTrailingZeros().toPlainString(), normalize(request.description()));

        return idempotencyService.execute(principal.id(), idempotencyKey, "TRANSFER", fingerprint,
                () -> executeTransfer(principal, request),
                this::toResponse);
    }

    @Transactional(readOnly = true)
    public TransferResponse getTransfer(SecurityUser principal, Long transactionId) {
        Transaction txn = transactionRepository.findById(transactionId)
                .filter(t -> t.getType() == TransactionType.TRANSFER)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer", transactionId));
        // Customers: only transfers they sent or received
        if (!principal.isStaff()
                && !txn.getFromAccount().getUser().getId().equals(principal.id())
                && !txn.getToAccount().getUser().getId().equals(principal.id())) {
            throw new ResourceNotFoundException("Transfer", transactionId);
        }
        return TransferResponse.from(txn);
    }

    /** Runs inside the transaction opened by IdempotencyService. */
    private Long executeTransfer(SecurityUser principal, TransferRequest request) {
        Account from = accountRepository.findByIdAndUserId(request.fromAccountId(), principal.id())
                .orElseThrow(() -> new ResourceNotFoundException("Account", request.fromAccountId()));
        Account to = accountRepository.findByAccountNumber(request.toAccountNumber().trim())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "No CoreTrust account exists with this account number."));

        boolean ownAccount = to.getUser().getId().equals(principal.id());
        if (!ownAccount && !beneficiaryRepository.existsByUserIdAndAccountNumberAndIfscCodeAndStatus(
                principal.id(), to.getAccountNumber(), bankIfsc, BeneficiaryStatus.ACTIVE)) {
            throw new BusinessException(ErrorCode.INVALID_TRANSFER,
                    "Add this account as a beneficiary before sending money to it.");
        }

        Transaction txn = ledgerService.transfer(from, to, request.amount(),
                trimToNull(request.description()), principal.id());
        auditService.record(AuditAction.MONEY_TRANSFERRED, AuditEntityType.TRANSACTION, txn.getId(),
                "%s: INR %s from %s to %s".formatted(txn.getTransactionReference(),
                        txn.getAmount().toPlainString(), from.getAccountNumber(), to.getAccountNumber()));
        return txn.getId();
    }

    private TransferResponse toResponse(Long transactionId) {
        return transactionRepository.findById(transactionId)
                .map(TransferResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer", transactionId));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String trimToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}