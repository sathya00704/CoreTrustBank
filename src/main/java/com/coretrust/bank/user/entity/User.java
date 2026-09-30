package com.coretrust.bank.user.entity;

import com.coretrust.bank.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_email", columnNames = "email"),
        @UniqueConstraint(name = "uk_users_phone", columnNames = "phone")
})
@Getter
@Setter
@NoArgsConstructor
public class User extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(nullable = false, length = 15)
    private String phone;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    /** Id of the one active login session; null when logged out */
    @Column(name = "active_session_id", length = 36)
    private String activeSessionId;

    /** When the current session's tokens expire (moves forward once on extension) */
    @Column(name = "session_valid_until")
    private Instant sessionValidUntil;

    public boolean hasActiveSession(Instant now) {
        return activeSessionId != null && sessionValidUntil != null && sessionValidUntil.isAfter(now);
    }

    public void startSession(String sessionId, Instant validUntil) {
        this.activeSessionId = sessionId;
        this.sessionValidUntil = validUntil;
    }

    public void extendSession(Instant validUntil) {
        this.sessionValidUntil = validUntil;
    }

    public void endSession() {
        this.activeSessionId = null;
        this.sessionValidUntil = null;
    }
}
