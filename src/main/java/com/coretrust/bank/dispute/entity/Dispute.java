package com.coretrust.bank.dispute.entity;

import com.coretrust.bank.common.entity.BaseEntity;
import com.coretrust.bank.common.exception.BusinessException;
import com.coretrust.bank.common.exception.ErrorCode;
import com.coretrust.bank.transaction.entity.Transaction;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "disputes", indexes = {
        @Index(name = "idx_disputes_status", columnList = "status"),
        @Index(name = "idx_disputes_transaction", columnList = "transaction_id")
})
@Getter
@Setter
@NoArgsConstructor
public class Dispute extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false, foreignKey = @ForeignKey(name = "fk_disputes_transaction"))
    private Transaction transaction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DisputeCategory category;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(name = "raised_by_id", nullable = false)
    private Long raisedById;

    @Column(name = "raised_by_email", nullable = false, length = 150)
    private String raisedByEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Setter(AccessLevel.NONE)
    private DisputeStatus status = DisputeStatus.OPEN;

    @Column(name = "resolved_by_id")
    @Setter(AccessLevel.NONE)
    private Long resolvedById;

    @Column(name = "resolved_by_email", length = 150)
    @Setter(AccessLevel.NONE)
    private String resolvedByEmail;

    @Column(name = "resolution_note", length = 255)
    @Setter(AccessLevel.NONE)
    private String resolutionNote;

    @Column(name = "resolved_at")
    @Setter(AccessLevel.NONE)
    private Instant resolvedAt;

    @Version
    @Setter(AccessLevel.NONE)
    private Long version;

    public void resolve(Long adminId, String adminEmail, String note) {
        close(DisputeStatus.RESOLVED, adminId, adminEmail, note);
    }

    public void reject(Long adminId, String adminEmail, String note) {
        close(DisputeStatus.REJECTED, adminId, adminEmail, note);
    }

    private void close(DisputeStatus outcome, Long adminId, String adminEmail, String note) {
        if (status != DisputeStatus.OPEN) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST_STATE, "This dispute is already " + status);
        }
        this.status = outcome;
        this.resolvedById = adminId;
        this.resolvedByEmail = adminEmail;
        this.resolutionNote = note;
        this.resolvedAt = Instant.now();
    }
}