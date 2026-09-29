package com.coretrust.bank.auth.entity;

import com.coretrust.bank.common.entity.BaseEntity;
import com.coretrust.bank.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "refresh_tokens",
        uniqueConstraints = @UniqueConstraint(name = "uk_refresh_tokens_hash", columnNames = "token_hash"),
        indexes = @Index(name = "idx_refresh_tokens_user_id", columnList = "user_id"))
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_refresh_tokens_user"))
    private User user;

    /** SHA-256 hex of the raw token; the raw token itself is never stored */
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked = false;

    /** Fixed end of the whole login session; never moves */
    @Column(name = "session_expires_at", nullable = false)
    private Instant sessionExpiresAt;

    /** True once the session has used its one extension */
    @Column(nullable = false)
    private boolean extended = false;
}