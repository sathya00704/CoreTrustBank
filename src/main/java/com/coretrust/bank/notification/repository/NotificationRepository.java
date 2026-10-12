package com.coretrust.bank.notification.repository;

import com.coretrust.bank.notification.entity.Notification;
import com.coretrust.bank.notification.entity.NotificationStatus;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUserId(Long userId, Pageable pageable);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    Page<Notification> findByUserIdAndStatus(Long userId, NotificationStatus status, Pageable pageable);

    long countByUserIdAndStatus(Long userId, NotificationStatus status);

    @Modifying
    @Query("""
            update Notification n set n.status = com.coretrust.bank.notification.entity.NotificationStatus.READ,
                   n.readAt = :now
            where n.user.id = :userId and n.status = com.coretrust.bank.notification.entity.NotificationStatus.UNREAD
            """)
    int markAllRead(@Param("userId") Long userId, @Param("now") Instant now);
}
