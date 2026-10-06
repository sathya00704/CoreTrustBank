package com.coretrust.bank.account.entity;

import com.coretrust.bank.common.entity.BaseEntity;
import com.coretrust.bank.user.entity.Role;
import com.coretrust.bank.user.entity.User;
import com.coretrust.bank.common.exception.BusinessException;
import com.coretrust.bank.common.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "accounts",
        uniqueConstraints = @UniqueConstraint(name = "uk_accounts_account_number", columnNames = "account_number"),
        indexes = @Index(name = "idx_accounts_user_id", columnList = "user_id"))
@Check(name = "chk_accounts_balance_non_negative", constraints = "balance >= 0")
@Getter
@Setter
@NoArgsConstructor
public class Account extends BaseEntity {

    @Column(name = "account_number", nullable = false, length = 20)
    private String accountNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_accounts_user"))
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private AccountType accountType;

    @Column(nullable = false, precision = 19, scale = 2)
    @Setter(AccessLevel.NONE)   // change only via debit() / credit()
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Setter(AccessLevel.NONE)   // change only via freeze() / unfreeze()
    private AccountStatus status = AccountStatus.ACTIVE;

    /** Who froze the account; decides whether the customer may self-unfreeze. Null when not frozen. */
    @Enumerated(EnumType.STRING)
    @Column(name = "frozen_by_role", length = 20)
    @Setter(AccessLevel.NONE)
    private Role frozenByRole;

    @Column(name = "closed_at")
    @Setter(AccessLevel.NONE)
    private Instant closedAt;

    @Version
    @Setter(AccessLevel.NONE)   // only Hibernate should touch this
    private Long version;

    public void freeze(Role frozenBy) {
        if (status != AccountStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.INVALID_ACCOUNT_STATE,
                    "Only ACTIVE accounts can be frozen. Current status: " + status);
        }
        status = AccountStatus.FROZEN;
        frozenByRole = frozenBy;
    }

    public void unfreeze() {
        if (status != AccountStatus.FROZEN) {
            throw new BusinessException(ErrorCode.INVALID_ACCOUNT_STATE,
                    "Only FROZEN accounts can be unfrozen. Current status: " + status);
        }
        status = AccountStatus.ACTIVE;
        frozenByRole = null;
    }

    /** Throws if this amount cannot be debited right now. Used for early checks and by debit(). */
    public void checkCanDebit(BigDecimal amount) {
        if (status != AccountStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.INVALID_ACCOUNT_STATE,
                    "Account %s is %s; money cannot be sent from it.".formatted(accountNumber, status));
        }
        if (balance.compareTo(amount) < 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_FUNDS, "Insufficient balance.");
        }
    }

    /** Money out: only ACTIVE accounts, never below zero. */
    public void debit(BigDecimal amount) {
        checkCanDebit(amount);
        balance = balance.subtract(amount);
    }

    /** Money in: allowed for ACTIVE and FROZEN accounts, not CLOSED. */
    public void credit(BigDecimal amount) {
        if (status == AccountStatus.CLOSED) {
            throw new BusinessException(ErrorCode.INVALID_ACCOUNT_STATE,
                    "Account %s is closed; it cannot receive money.".formatted(accountNumber));
        }
        balance = balance.add(amount);
    }

    /**
     * Bank-initiated correction (reversals): allowed on FROZEN accounts, since the bank is correcting
     * its own records, not the customer paying. Never on CLOSED accounts, never below zero.
     */
    public void debitForCorrection(BigDecimal amount) {
        if (status == AccountStatus.CLOSED) {
            throw new BusinessException(ErrorCode.INVALID_ACCOUNT_STATE,
                    "Account %s is closed; it cannot be debited.".formatted(accountNumber));
        }
        if (balance.compareTo(amount) < 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_FUNDS,
                    "Account %s no longer has enough balance for this reversal.".formatted(accountNumber));
        }
        balance = balance.subtract(amount);
    }

    /** Throws if the account cannot be closed right now. Used for early checks and by close(). */
    public void checkCanClose() {
        if (status != AccountStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.INVALID_ACCOUNT_STATE,
                    "Only ACTIVE accounts can be closed. Current status: " + status);
        }
        if (balance.signum() != 0) {
            throw new BusinessException(ErrorCode.INVALID_ACCOUNT_STATE,
                    "The balance must be zero before closing (current balance: INR %,.2f). "
                            .formatted(balance) + "Withdraw or transfer the remaining money first.");
        }
    }

    public void close() {
        checkCanClose();
        status = AccountStatus.CLOSED;
        closedAt = Instant.now();
    }
}
