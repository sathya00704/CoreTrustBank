package com.coretrust.bank.account.service;

import com.coretrust.bank.account.dto.AccountResponse;
import com.coretrust.bank.account.dto.BalanceResponse;
import com.coretrust.bank.account.dto.CreateAccountRequest;
import com.coretrust.bank.account.entity.Account;
import com.coretrust.bank.account.entity.AccountStatus;
import com.coretrust.bank.account.repository.AccountRepository;
import com.coretrust.bank.account.repository.AccountSpecifications;
import com.coretrust.bank.account.repository.UnfreezeRequestRepository;
import com.coretrust.bank.audit.entity.AuditAction;
import com.coretrust.bank.audit.entity.AuditEntityType;
import com.coretrust.bank.audit.service.AuditService;
import com.coretrust.bank.common.dto.PageResponse;
import com.coretrust.bank.common.exception.BusinessException;
import com.coretrust.bank.common.exception.ErrorCode;
import com.coretrust.bank.common.exception.ResourceNotFoundException;
import com.coretrust.bank.otp.service.OtpService;
import com.coretrust.bank.security.SecurityUser;
import com.coretrust.bank.user.entity.Role;
import com.coretrust.bank.user.repository.UserRepository;
import com.coretrust.bank.user.service.UserService;
import com.coretrust.bank.account.dto.AssistedAccountOtpRequest;
import com.coretrust.bank.account.dto.AssistedAccountRequest;
import com.coretrust.bank.account.entity.AccountType;
import com.coretrust.bank.otp.dto.OtpChallengeResponse;
import com.coretrust.bank.otp.entity.OtpPurpose;
import com.coretrust.bank.user.entity.User;
import com.coretrust.bank.account.entity.UnfreezeRequestStatus;
import com.coretrust.bank.account.repository.UnfreezeRequestRepository;
import com.coretrust.bank.otp.dto.OtpVerificationRequest;
import com.coretrust.bank.user.entity.Role;
import com.coretrust.bank.account.event.AccountFrozenEvent;
import com.coretrust.bank.account.event.AccountUnfrozenEvent;
import org.springframework.context.ApplicationEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AccountService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ACCOUNT_NUMBER_PREFIX = "1001";
    private static final int MAX_GENERATION_ATTEMPTS = 5;

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    private final UserService userService;
    private final OtpService otpService;

    private static final String OPEN_ACCOUNT_OPERATION = "OPEN_ACCOUNT|customer=%d|type=%s";

    private final UnfreezeRequestRepository unfreezeRequestRepository;

    private static final String SELF_UNFREEZE_OPERATION = "SELF_UNFREEZE|account=%d";

    private final ApplicationEventPublisher eventPublisher;

    @Value("${app.accounts.max-per-user}")
    private int maxAccountsPerUser;

    @Transactional
    public AccountResponse createAccount(SecurityUser principal, CreateAccountRequest request) {
        Account saved = openAccount(principal.id(), request.accountType());
        auditService.record(AuditAction.ACCOUNT_CREATED, AuditEntityType.ACCOUNT, saved.getId(),
                "%s account %s created".formatted(saved.getAccountType(), saved.getAccountNumber()));
        return AccountResponse.from(saved);
    }

    /** Customers see only their own accounts; staff see all, optionally filtered. */
    @Transactional(readOnly = true)
    public PageResponse<AccountResponse> listAccounts(SecurityUser principal, Long userId,
                                                      AccountStatus status, Pageable pageable) {
        Page<Account> page = principal.isStaff()
                ? accountRepository.findAll(AccountSpecifications.withFilters(userId, status), pageable)
                : accountRepository.findByUserId(principal.id(), pageable);   // customer filters ignored
        return PageResponse.from(page.map(AccountResponse::from));
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(SecurityUser principal, Long accountId) {
        return AccountResponse.from(findAccessibleAccount(principal, accountId));
    }

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(SecurityUser principal, Long accountId) {
        Account account = findAccessibleAccount(principal, accountId);
        return new BalanceResponse(account.getAccountNumber(), account.getBalance(),
                account.getCurrency(), Instant.now());
    }

    @Transactional
    public AccountResponse freeze(SecurityUser principal, Long accountId, String reason) {
        // Customers: own accounts only (404 otherwise). Admins: any account.
        Account account = findAccessibleAccount(principal, accountId);
        account.freeze(principal.role());

        String frozenBy = switch (principal.role()) {
            case CUSTOMER -> "customer (self-service)";
            case SUPPORT -> "support %s on customer request".formatted(principal.email());
            case ADMIN -> "admin " + principal.email();
        };
        auditService.record(AuditAction.ACCOUNT_FROZEN, AuditEntityType.ACCOUNT, accountId,
                "Account %s frozen by %s. Reason: %s".formatted(account.getAccountNumber(), frozenBy, reason));

        User owner = account.getUser();
        eventPublisher.publishEvent(new AccountFrozenEvent(account.getAccountNumber(),
                owner.getName(), owner.getEmail(), principal.role(), reason));
        return AccountResponse.from(account);
    }

//    @Transactional
//    public AccountResponse unfreeze(SecurityUser principal, Long accountId, String reason) {
//        if (principal.role() != Role.ADMIN) {
//            throw new AccessDeniedException("Only admins can unfreeze accounts directly.");
//        }
//        Account account = findAccount(accountId);
//        account.unfreeze();
//        auditService.record(AuditAction.ACCOUNT_UNFROZEN, AuditEntityType.ACCOUNT, accountId,
//                "Account %s unfrozen by admin %s. Reason: %s".formatted(
//                        account.getAccountNumber(), principal.email(), reason));
//        return AccountResponse.from(account);
//    }

    /** Way 1, step 1 (customer): email an OTP to the customer themselves. */
    @Transactional
    public OtpChallengeResponse requestSelfUnfreezeOtp(SecurityUser principal, Long accountId) {
        Account account = findSelfUnfreezableAccount(principal, accountId);
        User customer = userRepository.findById(principal.id())
                .orElseThrow(() -> new ResourceNotFoundException("User", principal.id()));
        return otpService.issue(customer, OtpPurpose.UNFREEZE_ACCOUNT,
                SELF_UNFREEZE_OPERATION.formatted(accountId),
                "unfreeze your account " + account.getAccountNumber(),
                principal.id());
    }

    /** Way 1, step 2 (customer): unfreeze with the OTP. */
    @Transactional
    public AccountResponse selfUnfreeze(SecurityUser principal, Long accountId, OtpVerificationRequest request) {
        Account account = findSelfUnfreezableAccount(principal, accountId);
        otpService.verify(request.otpId(), principal.id(), OtpPurpose.UNFREEZE_ACCOUNT,
                SELF_UNFREEZE_OPERATION.formatted(accountId), request.otpCode());

        account.unfreeze();

        // A support request raised meanwhile is no longer needed
        unfreezeRequestRepository.findByAccountIdAndStatus(accountId, UnfreezeRequestStatus.PENDING)
                .ifPresent(pending -> {
                    pending.cancel("Account unfrozen by the customer (self-service, OTP verified)");
                    auditService.record(AuditAction.UNFREEZE_CANCELLED, AuditEntityType.UNFREEZE_REQUEST,
                            pending.getId(), "Cancelled: customer unfroze the account themselves");
                });

        auditService.record(AuditAction.ACCOUNT_UNFROZEN, AuditEntityType.ACCOUNT, accountId,
                "Account %s unfrozen by customer (self-service, OTP verified)".formatted(account.getAccountNumber()));

        User owner = account.getUser();
        eventPublisher.publishEvent(new AccountUnfrozenEvent(account.getAccountNumber(),
                owner.getName(), owner.getEmail(), "you, using self-service (OTP verified)",
                "Unfrozen at your request"));
        return AccountResponse.from(account);
    }

    /** Own account, currently frozen, and frozen by the customer themselves. */
    private Account findSelfUnfreezableAccount(SecurityUser principal, Long accountId) {
        Account account = accountRepository.findByIdAndUserId(accountId, principal.id())
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        if (account.getStatus() != AccountStatus.FROZEN) {
            throw new BusinessException(ErrorCode.INVALID_ACCOUNT_STATE, "This account is not frozen.");
        }
        if (account.getFrozenByRole() != Role.CUSTOMER) {
            throw new BusinessException(ErrorCode.INVALID_ACCOUNT_STATE,
                    "This account was frozen by the bank. Please contact support to request an unfreeze.");
        }
        return account;
    }

    // ---------- helpers ----------

    /** Ownership check: a customer asking for someone else's account gets 404, not 403. */
    private Account findAccessibleAccount(SecurityUser principal, Long accountId) {
        return (principal.isStaff()
                ? accountRepository.findById(accountId)
                : accountRepository.findByIdAndUserId(accountId, principal.id()))
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
    }

    private Account findAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
    }

    private String generateUniqueAccountNumber() {
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            String candidate = ACCOUNT_NUMBER_PREFIX + "%08d".formatted(RANDOM.nextInt(100_000_000));
            if (!accountRepository.existsByAccountNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate a unique account number");
    }

    /** Step 1 (staff): email an OTP to the customer. */
    @Transactional
    public OtpChallengeResponse requestAssistedAccountOtp(SecurityUser staff, AssistedAccountOtpRequest request) {
        User customer = userService.findActiveCustomerByEmail(request.customerEmail());
        ensureBelowAccountLimit(customer.getId());   // fail before bothering the customer with an OTP
        return otpService.issue(customer, OtpPurpose.OPEN_ACCOUNT,
                OPEN_ACCOUNT_OPERATION.formatted(customer.getId(), request.accountType()),
                "open a new %s account".formatted(request.accountType()),
                staff.id());
    }

    /** Step 2 (staff): open the account once the customer has shared the OTP. */
    @Transactional
    public AccountResponse openAssistedAccount(SecurityUser staff, AssistedAccountRequest request) {
        User customer = userService.findActiveCustomerByEmail(request.customerEmail());
        otpService.verify(request.otpId(), customer.getId(), OtpPurpose.OPEN_ACCOUNT,
                OPEN_ACCOUNT_OPERATION.formatted(customer.getId(), request.accountType()), request.otpCode());

        Account saved = openAccount(customer.getId(), request.accountType());
        auditService.record(AuditAction.ACCOUNT_CREATED, AuditEntityType.ACCOUNT, saved.getId(),
                "%s account %s opened by %s %s for customer %s (OTP verified)".formatted(
                        saved.getAccountType(), saved.getAccountNumber(),
                        staff.role(), staff.email(), customer.getEmail()));
        return AccountResponse.from(saved);
    }

    private Account openAccount(Long ownerId, AccountType accountType) {
        ensureBelowAccountLimit(ownerId);
        Account account = new Account();
        account.setUser(userRepository.getReferenceById(ownerId));
        account.setAccountNumber(generateUniqueAccountNumber());
        account.setAccountType(accountType);
        return accountRepository.save(account);
    }

    private void ensureBelowAccountLimit(Long ownerId) {
        if (accountRepository.countByUserId(ownerId) >= maxAccountsPerUser) {
            throw new BusinessException(ErrorCode.ACCOUNT_LIMIT_REACHED,
                    "At most %d accounts are allowed per customer.".formatted(maxAccountsPerUser));
        }
    }
}