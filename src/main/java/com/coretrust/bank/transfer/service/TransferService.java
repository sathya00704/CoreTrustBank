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
import com.coretrust.bank.common.util.MaskingUtils;
import com.coretrust.bank.otp.dto.OtpChallengeResponse;
import com.coretrust.bank.otp.entity.OtpPurpose;
import com.coretrust.bank.otp.service.OtpService;
import com.coretrust.bank.transfer.dto.AssistedTransferOtpRequest;
import com.coretrust.bank.transfer.dto.AssistedTransferRequest;
import com.coretrust.bank.user.entity.User;
import com.coretrust.bank.user.service.UserService;

import java.math.BigDecimal;
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

    private final UserService userService;
    private final OtpService otpService;

    private static final String ASSISTED_TRANSFER_OPERATION =
            "ASSISTED_TRANSFER|customer=%d|from=%s|to=%s|amount=%s";

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
        Account to = resolveRecipient(principal.id(), request.toAccountNumber());

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

    /** Step 1 (staff): validate the whole transfer, then email the customer an OTP for it. */
    @Transactional
    public OtpChallengeResponse requestAssistedOtp(SecurityUser staff, AssistedTransferOtpRequest request) {
        User customer = userService.findActiveCustomerByEmail(request.customerEmail());
        Account from = findCustomerAccount(customer, request.fromAccountNumber());
        Account to = resolveRecipient(customer.getId(), request.toAccountNumber());
        from.checkCanDebit(request.amount());

        return otpService.issue(customer, OtpPurpose.ASSISTED_TRANSFER,
                assistedOperation(customer.getId(), from.getAccountNumber(), to.getAccountNumber(), request.amount()),
                "transfer INR %,.2f from your account %s to %s (%s) (requested by CoreTrust support on your behalf)"
                        .formatted(request.amount(), MaskingUtils.maskAccountNumber(from.getAccountNumber()),
                                MaskingUtils.maskAccountNumber(to.getAccountNumber()), to.getUser().getName()),
                staff.id());
    }

    /** Step 2 (staff): transfer with the customer's OTP. Idempotent. */
    public IdempotentResult<TransferResponse> assistedTransfer(SecurityUser staff, String idempotencyKey,
                                                               AssistedTransferRequest request) {
        String fingerprint = "customer=%s|from=%s|to=%s|amount=%s|desc=%s|otp=%d".formatted(
                request.customerEmail().trim().toLowerCase(), request.fromAccountNumber().trim(),
                request.toAccountNumber().trim(), request.amount().stripTrailingZeros().toPlainString(),
                normalize(request.description()), request.otpId());

        return idempotencyService.execute(staff.id(), idempotencyKey, "ASSISTED_TRANSFER", fingerprint, () -> {
            User customer = userService.findActiveCustomerByEmail(request.customerEmail());
            Account from = findCustomerAccount(customer, request.fromAccountNumber());
            Account to = resolveRecipient(customer.getId(), request.toAccountNumber());
            otpService.verify(request.otpId(), customer.getId(), OtpPurpose.ASSISTED_TRANSFER,
                    assistedOperation(customer.getId(), from.getAccountNumber(), to.getAccountNumber(), request.amount()),
                    request.otpCode());

            Transaction txn = ledgerService.transfer(from, to, request.amount(),
                    trimToNull(request.description()), staff.id());
            auditService.record(AuditAction.MONEY_TRANSFERRED, AuditEntityType.TRANSACTION, txn.getId(),
                    "%s: INR %s from %s to %s by %s %s for customer %s (OTP verified)".formatted(
                            txn.getTransactionReference(), txn.getAmount().toPlainString(),
                            from.getAccountNumber(), to.getAccountNumber(),
                            staff.role(), staff.email(), customer.getEmail()));
            return txn.getId();
        }, this::toResponse);
    }

    /** Recipient must be one of the owner's own accounts, or an active CoreTrust beneficiary of theirs. */
    private Account resolveRecipient(Long ownerId, String toAccountNumber) {
        Account to = accountRepository.findByAccountNumber(toAccountNumber.trim())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "No CoreTrust account exists with this account number."));
        boolean ownAccount = to.getUser().getId().equals(ownerId);
        if (!ownAccount && !beneficiaryRepository.existsByUserIdAndAccountNumberAndIfscCodeAndStatus(
                ownerId, to.getAccountNumber(), bankIfsc, BeneficiaryStatus.ACTIVE)) {
            throw new BusinessException(ErrorCode.INVALID_TRANSFER,
                    "Add this account as a beneficiary before sending money to it.");
        }
        return to;
    }

    private Account findCustomerAccount(User customer, String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber.trim())
                .filter(a -> a.getUser().getId().equals(customer.getId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "This customer has no account with that number."));
    }

    private static String assistedOperation(Long customerId, String from, String to, BigDecimal amount) {
        return ASSISTED_TRANSFER_OPERATION.formatted(customerId, from, to, amount.stripTrailingZeros().toPlainString());
    }
}