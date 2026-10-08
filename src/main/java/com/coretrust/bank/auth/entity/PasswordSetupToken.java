package com.coretrust.bank.auth.entity;

import com.coretrust.bank.common.entity.BaseEntity;
import com.coretrust.bank.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "password_setup_tokens",
        uniqueConstraints = @UniqueConstraint(name = "uk_password_setup_tokens_hash", columnNames = "token_hash"),
        indexes = @Index(name = "idx_password_setup_tokens_user", columnList = "user_id"))
@Getter
@Setter
@NoArgsConstructor
public class PasswordSetupToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_password_setup_tokens_user"))
    private User user;

    /** SHA-256 of the raw token; the raw token exists only in the email */
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PasswordTokenPurpose purpose;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    @Setter(AccessLevel.NONE)
    private Instant usedAt;

    public boolean isUsable(Instant now) {
        return usedAt == null && expiresAt.isAfter(now);
    }

    public void markUsed() {
        usedAt = Instant.now();
    }
}