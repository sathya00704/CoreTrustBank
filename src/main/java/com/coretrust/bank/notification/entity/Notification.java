package com.coretrust.bank.notification.entity;

import com.coretrust.bank.common.entity.BaseEntity;
import com.coretrust.bank.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "notifications",
        indexes = @Index(name = "idx_notifications_user_status", columnList = "user_id, status"))
@Getter
@Setter
@NoArgsConstructor
public class Notification extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_notifications_user"))
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationType type;

    @Column(nullable = false, length = 500)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Setter(AccessLevel.NONE)
    private NotificationStatus status = NotificationStatus.UNREAD;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(name = "read_at")
    @Setter(AccessLevel.NONE)
    private Instant readAt;

    /** Idempotent: marking an already-read notification is a no-op. */
    public void markRead() {
        if (status == NotificationStatus.UNREAD) {
            status = NotificationStatus.READ;
            readAt = Instant.now();
        }
    }
}
