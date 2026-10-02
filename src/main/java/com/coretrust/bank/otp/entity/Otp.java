package com.coretrust.bank.otp.entity;

import com.coretrust.bank.common.entity.BaseEntity;
import com.coretrust.bank.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "otps", indexes = @Index(name = "idx_otps_user_purpose", columnList = "user_id, purpose"))
@Getter
@Setter
@NoArgsConstructor
public class Otp extends BaseEntity {

    /** The customer who must approve */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_otps_user"))
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OtpPurpose purpose;

    /** BCrypt hash of the 6-digit code */
    @Column(name = "code_hash", nullable = false, length = 100)
    private String codeHash;

    /** SHA-256 of the exact operation details the OTP authorizes */
    @Column(name = "operation_hash", nullable = false, length = 64)
    private String operationHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    @Setter(AccessLevel.NONE)
    private int attempts = 0;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(nullable = false)
    @Setter(AccessLevel.NONE)
    private boolean consumed = false;

    /** Staff member who requested it */
    @Column(name = "requested_by")
    private Long requestedBy;

    @Version
    @Setter(AccessLevel.NONE)
    private Long version;

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public boolean hasAttemptsLeft() {
        return attempts < maxAttempts;
    }

    public int remainingAttempts() {
        return Math.max(0, maxAttempts - attempts);
    }

    public void registerFailedAttempt() {
        attempts++;
    }

    public void markConsumed() {
        consumed = true;
    }
}