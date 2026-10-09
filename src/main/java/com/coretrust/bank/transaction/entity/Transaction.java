package com.coretrust.bank.transaction.entity;

import com.coretrust.bank.account.entity.Account;
import com.coretrust.bank.common.entity.BaseEntity;
import com.coretrust.bank.common.exception.BusinessException;
import com.coretrust.bank.common.exception.ErrorCode;
import lombok.AccessLevel;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;

import java.math.BigDecimal;

@Entity
@Table(name = "transactions",
        uniqueConstraints = @UniqueConstraint(name = "uk_transactions_reference", columnNames = "transaction_reference"),
        indexes = {
                @Index(name = "idx_transactions_from_account_created", columnList = "from_account_id, created_at"),
                @Index(name = "idx_transactions_to_account_created", columnList = "to_account_id, created_at"),
                @Index(name = "idx_transactions_status", columnList = "status")
        })
@Check(name = "chk_transactions_amount_positive", constraints = "amount > 0")
@Getter
@Setter
@NoArgsConstructor
public class Transaction extends BaseEntity {

    @Column(name = "transaction_reference", nullable = false, length = 40)
    private String transactionReference;

    /** Null for DEPOSIT */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_account_id", foreignKey = @ForeignKey(name = "fk_transactions_from_account"))
    private Account fromAccount;

    /** Null for WITHDRAWAL */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_account_id", foreignKey = @ForeignKey(name = "fk_transactions_to_account"))
    private Account toAccount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status = TransactionStatus.PENDING;

    @Column(length = 255)
    private String description;

    /** Who performed it: the customer, or the staff member for branch operations */
    @Column(name = "initiated_by_id")
    private Long initiatedById;

    /** Balances right after this transaction; needed for statements (Phase 6) */
    @Column(name = "from_balance_after", precision = 19, scale = 2)
    private BigDecimal fromBalanceAfter;

    @Column(name = "to_balance_after", precision = 19, scale = 2)
    private BigDecimal toBalanceAfter;

    /** Set on REVERSAL transactions: the transaction being undone */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_transaction_id",
            foreignKey = @ForeignKey(name = "fk_transactions_original"))
    private Transaction originalTransaction;

    /** Admin marker for investigation. Visible to staff only; never shown to customers. */
    @Column(nullable = false)
    @ColumnDefault("false")
    @Setter(AccessLevel.NONE)
    private boolean flagged = false;

    @Column(name = "flag_reason", length = 255)
    @Setter(AccessLevel.NONE)
    private String flagReason;

    @Column(name = "flagged_by_email", length = 150)
    @Setter(AccessLevel.NONE)
    private String flaggedByEmail;

    @Column(name = "flagged_at")
    @Setter(AccessLevel.NONE)
    private Instant flaggedAt;

    @Column(name = "flagged_by_id")
    @Setter(AccessLevel.NONE)
    private Long flaggedById;

    public void flag(Long flaggedById, String flaggedByEmail, String reason) {
        if (flagged) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST_STATE, "This transaction is already flagged.");
        }
        this.flagged = true;
        this.flagReason = reason;
        this.flaggedById = flaggedById;
        this.flaggedByEmail = flaggedByEmail;
        this.flaggedAt = Instant.now();
    }

    public void unflag() {
        if (!flagged) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST_STATE, "This transaction is not flagged.");
        }
        this.flagged = false;
        this.flagReason = null;
        this.flaggedById = null;
        this.flaggedByEmail = null;
        this.flaggedAt = null;
    }
}
