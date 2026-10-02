package com.coretrust.bank.account.entity;

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
@Table(name = "unfreeze_requests", indexes = {
        @Index(name = "idx_unfreeze_requests_status", columnList = "status"),
        @Index(name = "idx_unfreeze_requests_account", columnList = "account_id")
})
@Getter
@Setter
@NoArgsConstructor
public class UnfreezeRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, foreignKey = @ForeignKey(name = "fk_unfreeze_requests_account"))
    private Account account;

    @Column(name = "requested_by_id", nullable = false)
    private Long requestedById;

    @Column(name = "requested_by_email", nullable = false, length = 150)
    private String requestedByEmail;

    @Column(nullable = false, length = 255)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Setter(AccessLevel.NONE)
    private UnfreezeRequestStatus status = UnfreezeRequestStatus.PENDING;

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

    /** Two admins reviewing at once: the second fails instead of overwriting */
    @Version
    @Setter(AccessLevel.NONE)
    private Long version;

    public void approve(Long reviewerId, String reviewerEmail, String note) {
        review(UnfreezeRequestStatus.APPROVED, reviewerId, reviewerEmail, note);
    }

    public void reject(Long reviewerId, String reviewerEmail, String note) {
        review(UnfreezeRequestStatus.REJECTED, reviewerId, reviewerEmail, note);
    }

    private void review(UnfreezeRequestStatus decision, Long reviewerId, String reviewerEmail, String note) {
        if (status != UnfreezeRequestStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST_STATE, "This request has already been " + status);
        }
        this.status = decision;
        this.reviewedById = reviewerId;
        this.reviewedByEmail = reviewerEmail;
        this.reviewNote = note;
        this.reviewedAt = Instant.now();
    }

    /** Closed without review, e.g. the customer unfroze the account themselves meanwhile. */
    public void cancel(String note) {
        if (status != UnfreezeRequestStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST_STATE, "This request has already been " + status);
        }
        this.status = UnfreezeRequestStatus.CANCELLED;
        this.reviewNote = note;
        this.reviewedAt = Instant.now();
    }
}