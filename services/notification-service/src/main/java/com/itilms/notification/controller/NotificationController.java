package com.itilms.notification.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.itilms.common.dto.ApiMessage;
import com.itilms.common.dto.PageResponse;
import com.itilms.notification.dto.NotificationDtos.NotificationResponse;
import com.itilms.notification.dto.NotificationDtos.UnreadCountResponse;
import com.itilms.notification.service.InboxService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** The bell icon (Doc S11, S16). Every endpoint works on the caller's own notifications. */
@Tag(name = "Notifications", description = "The signed-in user's notifications")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class NotificationController {

    private final InboxService inbox;

    @Operation(summary = "My notifications", description = "Newest first.")
    @GetMapping
    public PageResponse<NotificationResponse> list(@RequestParam(defaultValue = "false") boolean unreadOnly,
                                                   @PageableDefault(size = 20) Pageable pageable) {
        return inbox.list(unreadOnly, pageable);
    }

    @Operation(summary = "How many are unread", description = "Cheap enough to poll for the bell badge.")
    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount() {
        return new UnreadCountResponse(inbox.unreadCount());
    }

    @Operation(summary = "Mark one as read")
    @PutMapping("/{id}/read")
    public NotificationResponse markRead(@PathVariable Long id) {
        return inbox.markRead(id);
    }

    @Operation(summary = "Mark all as read")
    @PutMapping("/read-all")
    public ApiMessage markAllRead() {
        int count = inbox.markAllRead();
        return ApiMessage.ok(count == 1 ? "1 notification marked as read." : count + " notifications marked as read.");
    }
}
