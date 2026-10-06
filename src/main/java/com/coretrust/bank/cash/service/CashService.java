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
import com.coretrust.bank.cash.dto.WithdrawalOtpRequest;
import com.coretrust.bank.cash.dto.WithdrawalRequest;
import com.coretrust.bank.common.util.MaskingUtils;
import com.coretrust.bank.otp.dto.OtpChallengeResponse;
import com.coretrust.bank.otp.entity.OtpPurpose;
import com.coretrust.bank.otp.service.OtpService;
import com.coretrust.bank.user.entity.User;
import com.coretrust.bank.user.service.UserService;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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

    private final UserService userService;
    private final OtpService otpService;

    private static final String WITHDRAWAL_OPERATION = "CASH_WITHDRAWAL|customer=%d|account=%s|amount=%s";

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

    /** Step 1 (staff): check everything, then email the customer an OTP for this exact amount. */
    @Transactional
    public OtpChallengeResponse requestWithdrawalOtp(SecurityUser staff, WithdrawalOtpRequest request) {
        User customer = userService.findActiveCustomerByEmail(request.customerEmail());
        Account account = findCustomerAccount(customer, request.accountNumber());
        account.checkCanDebit(request.amount());   // early check; the real one happens at execution

        return otpService.issue(customer, OtpPurpose.CASH_WITHDRAWAL,
                withdrawalOperation(customer.getId(), account.getAccountNumber(), request.amount()),
                "withdraw INR %,.2f in cash from your account %s (requested by CoreTrust support on your behalf)"
                        .formatted(request.amount(), MaskingUtils.maskAccountNumber(account.getAccountNumber())),
                staff.id());
    }

    /** Step 2 (staff): withdraw with the customer's OTP. Idempotent. */
    public IdempotentResult<CashTransactionResponse> withdraw(SecurityUser staff, String idempotencyKey,
                                                              WithdrawalRequest request) {
        String fingerprint = "customer=%s|account=%s|amount=%s|desc=%s|otp=%d".formatted(
                request.customerEmail().trim().toLowerCase(), request.accountNumber().trim(),
                request.amount().stripTrailingZeros().toPlainString(),
                request.description() == null ? "" : request.description().trim(), request.otpId());

        return idempotencyService.execute(staff.id(), idempotencyKey, "WITHDRAWAL", fingerprint, () -> {
            User customer = userService.findActiveCustomerByEmail(request.customerEmail());
            Account account = findCustomerAccount(customer, request.accountNumber());
            otpService.verify(request.otpId(), customer.getId(), OtpPurpose.CASH_WITHDRAWAL,
                    withdrawalOperation(customer.getId(), account.getAccountNumber(), request.amount()),
                    request.otpCode());

            String description = (request.description() == null || request.description().isBlank())
                    ? "Cash withdrawal" : request.description().trim();
            Transaction txn = ledgerService.withdraw(account, request.amount(), description, staff.id());

            auditService.record(AuditAction.MONEY_WITHDRAWN, AuditEntityType.TRANSACTION, txn.getId(),
                    "%s: INR %s withdrawn from %s by %s %s for customer %s (OTP verified)".formatted(
                            txn.getTransactionReference(), txn.getAmount().toPlainString(),
                            account.getAccountNumber(), staff.role(), staff.email(), customer.getEmail()));
            return txn.getId();
        }, this::toResponse);
    }

    /** The account must belong to this customer; anything else looks like "not found". */
    private Account findCustomerAccount(User customer, String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber.trim())
                .filter(a -> a.getUser().getId().equals(customer.getId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "This customer has no account with that number."));
    }

    private static String withdrawalOperation(Long customerId, String accountNumber, BigDecimal amount) {
        return WITHDRAWAL_OPERATION.formatted(customerId, accountNumber, amount.stripTrailingZeros().toPlainString());
    }
}