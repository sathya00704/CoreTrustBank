package com.coretrust.bank.notification.service;

import com.coretrust.bank.common.dto.PageResponse;
import com.coretrust.bank.common.exception.ResourceNotFoundException;
import com.coretrust.bank.common.util.SortValidator;
import com.coretrust.bank.notification.dto.NotificationResponse;
import com.coretrust.bank.notification.dto.UnreadCountResponse;
import com.coretrust.bank.notification.entity.Notification;
import com.coretrust.bank.notification.entity.NotificationStatus;
import com.coretrust.bank.notification.entity.NotificationType;
import com.coretrust.bank.notification.repository.NotificationRepository;
import com.coretrust.bank.security.SecurityUser;
import com.coretrust.bank.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final Set<String> SORTABLE = Set.of("createdAt");
    private static final int MAX_MESSAGE_LENGTH = 500;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    /**
     * Joins the caller's transaction, so the notification commits (or rolls back) with the business change.
     * Events carry the email, not the id; Phase 9 events will carry ids and remove this lookup.
     */
    @Transactional
    public void notifyUser(String email, NotificationType type, String title, String message) {
        userRepository.findByEmail(email).ifPresent(user -> {
            Notification notification = new Notification();
            notification.setUser(user);
            notification.setType(type);
            notification.setTitle(title);
            notification.setMessage(message.length() <= MAX_MESSAGE_LENGTH
                    ? message : message.substring(0, MAX_MESSAGE_LENGTH));
            notificationRepository.save(notification);
        });
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(SecurityUser viewer, NotificationStatus status, Pageable pageable) {
        SortValidator.requireAllowed(pageable.getSort(), SORTABLE);
        Page<Notification> page = (status == null)
                ? notificationRepository.findByUserId(viewer.id(), pageable)
                : notificationRepository.findByUserIdAndStatus(viewer.id(), status, pageable);
        return PageResponse.from(page.map(NotificationResponse::from));
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount(SecurityUser viewer) {
        return new UnreadCountResponse(
                notificationRepository.countByUserIdAndStatus(viewer.id(), NotificationStatus.UNREAD));
    }

    @Transactional
    public NotificationResponse markRead(SecurityUser viewer, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, viewer.id())
                .orElseThrow(() -> new ResourceNotFoundException("Notification", notificationId));
        notification.markRead();
        return NotificationResponse.from(notification);
    }

    @Transactional
    public int markAllRead(SecurityUser viewer) {
        return notificationRepository.markAllRead(viewer.id(), Instant.now());
    }
}