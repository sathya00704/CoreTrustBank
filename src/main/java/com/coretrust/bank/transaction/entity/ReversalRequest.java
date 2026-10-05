package com.coretrust.bank.transaction.entity;

import com.coretrust.bank.common.entity.BaseEntity;
import com.coretrust.bank.common.exception.BusinessException;
import com.coretrust.bank.common.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "reversal_requests", indexes = {
        @Index(name = "idx_reversal_requests_status", columnList = "status"),
        @Index(name = "idx_reversal_requests_transaction", columnList = "transaction_id")
})
@Getter
@Setter
@NoArgsConstructor
public class ReversalRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_reversal_requests_transaction"))
    private Transaction transaction;

    @Column(name = "requested_by_id", nullable = false)
    private Long requestedById;

    @Column(name = "requested_by_email", nullable = false, length = 150)
    private String requestedByEmail;

    @Column(nullable = false, length = 255)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Setter(AccessLevel.NONE)
    private ReversalRequestStatus status = ReversalRequestStatus.PENDING;

    @Column(name = "reviewed_by_id")
    @Setter(AccessLevel.NONE)
    private Long reviewedById;

    @Column(name = "reviewed_by_email", length = 150)
    @Setter(AccessLevel.NONE)
    private String reviewedByEmail;

    @Column(name = "review_note", length = 255)
    @Setter(AccessLevel.NONE)
    private String reviewNote;

    @Column(name = "reviewed_at")
    @Setter(AccessLevel.NONE)
    private Instant reviewedAt;

    /** The REVERSAL transaction created on approval */
    @Column(name = "reversal_transaction_id")
    @Setter(AccessLevel.NONE)
    private Long reversalTransactionId;

    @Column(name = "reversal_reference", length = 40)
    @Setter(AccessLevel.NONE)
    private String reversalReference;

    @Version
    @Setter(AccessLevel.NONE)
    private Long version;

    public void approve(Long reviewerId, String reviewerEmail, String note, Transaction reversal) {
        review(ReversalRequestStatus.APPROVED, reviewerId, reviewerEmail, note);
        this.reversalTransactionId = reversal.getId();
        this.reversalReference = reversal.getTransactionReference();
    }

    public void reject(Long reviewerId, String reviewerEmail, String note) {
        review(ReversalRequestStatus.REJECTED, reviewerId, reviewerEmail, note);
    }

    private void review(ReversalRequestStatus decision, Long reviewerId, String reviewerEmail, String note) {
        if (status != ReversalRequestStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST_STATE, "This request has already been " + status);
        }
        this.status = decision;
        this.reviewedById = reviewerId;
        this.reviewedByEmail = reviewerEmail;
        this.reviewNote = note;
        this.reviewedAt = Instant.now();
    }
}