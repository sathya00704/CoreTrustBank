package com.coretrust.bank.transaction.entity;

import com.coretrust.bank.account.entity.Account;
import com.coretrust.bank.common.entity.BaseEntity;
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
}
