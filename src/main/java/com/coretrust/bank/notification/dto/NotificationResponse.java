package com.coretrust.bank.notification.dto;

import com.coretrust.bank.notification.entity.Notification;
import com.coretrust.bank.notification.entity.NotificationStatus;
import com.coretrust.bank.notification.entity.NotificationType;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        NotificationType type,
        String title,
        String message,
        NotificationStatus status,
        Instant createdAt,
        Instant readAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getMessage(),
                n.getStatus(), n.getCreatedAt(), n.getReadAt());
    }
}