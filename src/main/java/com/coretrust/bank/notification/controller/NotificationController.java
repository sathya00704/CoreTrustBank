package com.coretrust.bank.notification.controller;

import com.coretrust.bank.common.dto.MessageResponse;
import com.coretrust.bank.common.dto.PageResponse;
import com.coretrust.bank.notification.dto.NotificationResponse;
import com.coretrust.bank.notification.dto.UnreadCountResponse;
import com.coretrust.bank.notification.entity.NotificationStatus;
import com.coretrust.bank.notification.service.NotificationService;
import com.coretrust.bank.security.SecurityUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Notifications", description = "The in-app inbox of the logged-in user")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public PageResponse<NotificationResponse> list(
            @AuthenticationPrincipal SecurityUser principal,
            @RequestParam(required = false) NotificationStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return notificationService.list(principal, status, pageable);
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount(@AuthenticationPrincipal SecurityUser principal) {
        return notificationService.unreadCount(principal);
    }

    @PatchMapping("/{id}/read")
    public NotificationResponse markRead(@AuthenticationPrincipal SecurityUser principal, @PathVariable Long id) {
        return notificationService.markRead(principal, id);
    }

    @PatchMapping("/read-all")
    public MessageResponse markAllRead(@AuthenticationPrincipal SecurityUser principal) {
        int updated = notificationService.markAllRead(principal);
        return MessageResponse.of("%d notification(s) marked as read.".formatted(updated));
    }
}