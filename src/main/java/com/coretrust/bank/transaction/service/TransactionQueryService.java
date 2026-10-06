package com.coretrust.bank.transaction.service;

import com.coretrust.bank.account.entity.Account;
import com.coretrust.bank.account.repository.AccountRepository;
import com.coretrust.bank.common.dto.PageResponse;
import com.coretrust.bank.common.exception.BusinessException;
import com.coretrust.bank.common.exception.ErrorCode;
import com.coretrust.bank.common.exception.ResourceNotFoundException;
import com.coretrust.bank.common.util.MaskingUtils;
import com.coretrust.bank.common.util.SortValidator;
import com.coretrust.bank.security.SecurityUser;
import com.coretrust.bank.transaction.dto.Direction;
import com.coretrust.bank.transaction.dto.TransactionFilter;
import com.coretrust.bank.transaction.dto.TransactionResponse;
import com.coretrust.bank.transaction.entity.Transaction;
import com.coretrust.bank.transaction.repository.TransactionRepository;
import com.coretrust.bank.transaction.repository.TransactionSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TransactionQueryService {

    private static final Set<String> SORTABLE = Set.of("createdAt", "amount");

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;

    @Value("${app.timezone}")
    private String timezone;

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> list(SecurityUser viewer, TransactionFilter filter, Pageable pageable) {
        SortValidator.requireAllowed(pageable.getSort(), SORTABLE);
        validateRanges(filter);

        if (!viewer.isStaff() && filter.accountId() != null) {
            // A customer filtering by someone else's account gets 404, not an empty list
            accountRepository.findByIdAndUserId(filter.accountId(), viewer.id())
                    .orElseThrow(() -> new ResourceNotFoundException("Account", filter.accountId()));
        }

        Long restrictToUser = viewer.isStaff() ? null : viewer.id();
        Long staffUserFilter = viewer.isStaff() ? filter.userId() : null;

        Page<Transaction> page = transactionRepository.findAll(
                TransactionSpecifications.matching(filter, restrictToUser, staffUserFilter, ZoneId.of(timezone)),
                pageable);
        return PageResponse.from(page.map(t -> toView(t, viewer, filter.accountId())));
    }

    @Transactional(readOnly = true)
    public TransactionResponse get(SecurityUser viewer, Long transactionId) {
        Transaction txn = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", transactionId));
        if (!viewer.isStaff() && !isOwnedBy(txn.getFromAccount(), viewer.id())
                && !isOwnedBy(txn.getToAccount(), viewer.id())) {
            throw new ResourceNotFoundException("Transaction", transactionId);
        }
        return toView(txn, viewer, null);
    }

    // ---------- helpers ----------

    private static void validateRanges(TransactionFilter f) {
        if (f.fromDate() != null && f.toDate() != null && f.fromDate().isAfter(f.toDate())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "fromDate must not be after toDate.");
        }
        if (f.minAmount() != null && f.maxAmount() != null && f.minAmount().compareTo(f.maxAmount()) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "minAmount must not be greater than maxAmount.");
        }
    }

    /**
     * Builds the row from one account's point of view: the requested accountId, or for customers
     * their own side of the transaction. Staff without an accountId get no direction or balance.
     */
    private TransactionResponse toView(Transaction t, SecurityUser viewer, Long perspectiveAccountId) {
        Account from = t.getFromAccount();
        Account to = t.getToAccount();

        Long perspective = perspectiveAccountId;
        if (perspective == null && !viewer.isStaff()) {
            perspective = isOwnedBy(from, viewer.id()) ? from.getId() : to.getId();
        }

        Direction direction = null;
        BigDecimal balanceAfter = null;
        if (perspective != null) {
            if (from != null && from.getId().equals(perspective)) {
                direction = Direction.DEBIT;
                balanceAfter = t.getFromBalanceAfter();
            } else if (to != null && to.getId().equals(perspective)) {
                direction = Direction.CREDIT;
                balanceAfter = t.getToBalanceAfter();
            }
        }

        return new TransactionResponse(t.getId(), t.getTransactionReference(), t.getType(), t.getStatus(),
                t.getAmount(), t.getCurrency(), t.getDescription(),
                displayNumber(from, viewer), holderName(from),
                displayNumber(to, viewer), holderName(to),
                direction, balanceAfter,
                t.getOriginalTransaction() == null ? null : t.getOriginalTransaction().getTransactionReference(),
                t.getCreatedAt());
    }

    /** Staff and the account's owner see the full number; anyone else sees it masked. */
    private static String displayNumber(Account account, SecurityUser viewer) {
        if (account == null) {
            return null;
        }
        return (viewer.isStaff() || isOwnedBy(account, viewer.id()))
                ? account.getAccountNumber()
                : MaskingUtils.maskAccountNumber(account.getAccountNumber());
    }

    private static String holderName(Account account) {
        return account == null ? null : account.getUser().getName();
    }

    private static boolean isOwnedBy(Account account, Long userId) {
        return account != null && account.getUser().getId().equals(userId);
    }
}