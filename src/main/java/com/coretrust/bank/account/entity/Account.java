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

    /** Money out: only ACTIVE accounts, never below zero. */
    public void debit(BigDecimal amount) {
        if (status != AccountStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.INVALID_ACCOUNT_STATE,
                    "Account %s is %s; money cannot be sent from it.".formatted(accountNumber, status));
        }
        if (balance.compareTo(amount) < 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_FUNDS, "Insufficient balance.");
        }
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
}
