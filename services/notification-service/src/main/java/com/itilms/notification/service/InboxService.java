package com.itilms.notification.service;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.common.dto.PageResponse;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.common.security.AppPrincipal;
import com.itilms.common.security.SecurityUtils;
import com.itilms.notification.dto.NotificationDtos.NotificationResponse;
import com.itilms.notification.entity.Notification;
import com.itilms.notification.repository.NotificationRepository;

import lombok.RequiredArgsConstructor;

/** The caller's own notifications. Nobody, staff included, reads another person's. */
@Service
@RequiredArgsConstructor
public class InboxService {

    private final NotificationRepository notifications;
    private final DirectoryService directory;

    @Transactional
    public PageResponse<NotificationResponse> list(boolean unreadOnly, Pageable pageable) {
        AppPrincipal me = SecurityUtils.requirePrincipal();
        directory.remember(me);
        Page<Notification> page = unreadOnly
                ? notifications.findByUserIdAndReadAtIsNullOrderByCreatedAtDescIdDesc(me.userId(), pageable)
                : notifications.findByUserIdOrderByCreatedAtDescIdDesc(me.userId(), pageable);
        return PageResponse.from(page, NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return notifications.countByUserIdAndReadAtIsNull(SecurityUtils.currentUserId());
    }

    /** Marking an already-read notification again is harmless and keeps the first read time. */
    @Transactional
    public NotificationResponse markRead(Long id) {
        Long me = SecurityUtils.currentUserId();
        notifications.markRead(id, me, Instant.now());
        // Someone else's id gives the same answer as one that does not exist.
        return notifications.findByIdAndUserId(id, me).map(NotificationResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", id));
    }

    @Transactional
    public int markAllRead() {
        return notifications.markAllRead(SecurityUtils.currentUserId(), Instant.now());
    }
}
