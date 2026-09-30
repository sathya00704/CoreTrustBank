package com.coretrust.bank.account.service;

import com.coretrust.bank.account.dto.AccountResponse;
import com.coretrust.bank.account.dto.BalanceResponse;
import com.coretrust.bank.account.dto.CreateAccountRequest;
import com.coretrust.bank.account.entity.Account;
import com.coretrust.bank.account.entity.AccountStatus;
import com.coretrust.bank.account.repository.AccountRepository;
import com.coretrust.bank.account.repository.AccountSpecifications;
import com.coretrust.bank.audit.entity.AuditAction;
import com.coretrust.bank.audit.entity.AuditEntityType;
import com.coretrust.bank.audit.service.AuditService;
import com.coretrust.bank.common.dto.PageResponse;
import com.coretrust.bank.common.exception.BusinessException;
import com.coretrust.bank.common.exception.ErrorCode;
import com.coretrust.bank.common.exception.ResourceNotFoundException;
import com.coretrust.bank.security.SecurityUser;
import com.coretrust.bank.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @Value("${app.accounts.max-per-user}")
    private int maxAccountsPerUser;

    @Transactional
    public AccountResponse createAccount(SecurityUser principal, CreateAccountRequest request) {
        if (accountRepository.countByUserId(principal.id()) >= maxAccountsPerUser) {
            throw new BusinessException(ErrorCode.ACCOUNT_LIMIT_REACHED,
                    "You can have at most %d accounts.".formatted(maxAccountsPerUser));
        }

        Account account = new Account();
        account.setUser(userRepository.getReferenceById(principal.id()));
        account.setAccountNumber(generateUniqueAccountNumber());
        account.setAccountType(request.accountType());

        Account saved = accountRepository.save(account);
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
        account.freeze();

        String frozenBy = principal.isStaff()
                ? "admin " + principal.email()
                : "customer (self-service)";
        auditService.record(AuditAction.ACCOUNT_FROZEN, AuditEntityType.ACCOUNT, accountId,
                "Account %s frozen by %s. Reason: %s".formatted(account.getAccountNumber(), frozenBy, reason));
        return AccountResponse.from(account);
    }

    @Transactional
    public AccountResponse unfreeze(Long accountId, String reason) {
        Account account = findAccount(accountId);
        account.unfreeze();
        auditService.record(AuditAction.ACCOUNT_UNFROZEN, AuditEntityType.ACCOUNT, accountId,
                "Account %s unfrozen. Reason: %s".formatted(account.getAccountNumber(), reason));
        return AccountResponse.from(account);
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
}