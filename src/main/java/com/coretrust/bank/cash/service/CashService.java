package com.coretrust.bank.cash.service;

import com.coretrust.bank.account.entity.Account;
import com.coretrust.bank.account.repository.AccountRepository;
import com.coretrust.bank.audit.entity.AuditAction;
import com.coretrust.bank.audit.entity.AuditEntityType;
import com.coretrust.bank.audit.service.AuditService;
import com.coretrust.bank.cash.dto.CashTransactionResponse;
import com.coretrust.bank.cash.dto.DepositRequest;
import com.coretrust.bank.common.exception.BusinessException;
import com.coretrust.bank.common.exception.ErrorCode;
import com.coretrust.bank.common.exception.ResourceNotFoundException;
import com.coretrust.bank.idempotency.dto.IdempotentResult;
import com.coretrust.bank.idempotency.service.IdempotencyService;
import com.coretrust.bank.security.SecurityUser;
import com.coretrust.bank.transaction.entity.Transaction;
import com.coretrust.bank.transaction.repository.TransactionRepository;
import com.coretrust.bank.transaction.service.LedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CashService {

    private final IdempotencyService idempotencyService;
    private final LedgerService ledgerService;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AuditService auditService;

    /** Branch cash deposit by staff. No OTP: money only comes in. Frozen accounts may receive it. */
    public IdempotentResult<CashTransactionResponse> deposit(SecurityUser staff, String idempotencyKey,
                                                             DepositRequest request) {
        String fingerprint = "account=%s|amount=%s|desc=%s".formatted(request.accountNumber().trim(),
                request.amount().stripTrailingZeros().toPlainString(),
                request.description() == null ? "" : request.description().trim());

        return idempotencyService.execute(staff.id(), idempotencyKey, "DEPOSIT", fingerprint, () -> {
            Account account = accountRepository.findByAccountNumber(request.accountNumber().trim())
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                            "No CoreTrust account exists with this account number."));
            String description = (request.description() == null || request.description().isBlank())
                    ? "Cash deposit" : request.description().trim();

            Transaction txn = ledgerService.deposit(account, request.amount(), description, staff.id());
            auditService.record(AuditAction.MONEY_DEPOSITED, AuditEntityType.TRANSACTION, txn.getId(),
                    "%s: INR %s deposited to %s by %s %s".formatted(txn.getTransactionReference(),
                            txn.getAmount().toPlainString(), account.getAccountNumber(), staff.role(), staff.email()));
            return txn.getId();
        }, this::toResponse);
    }

    private CashTransactionResponse toResponse(Long transactionId) {
        return transactionRepository.findById(transactionId)
                .map(CashTransactionResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", transactionId));
    }
}   