package com.itilms.notification.dto;

import java.time.Instant;

import com.itilms.notification.entity.Announcement;
import com.itilms.notification.entity.Notification;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request and response bodies for notifications and announcements. */
public final class NotificationDtos {

    private NotificationDtos() {
    }

    public record NotificationResponse(Long id, String type, String title, String message, String actionUrl,
                                       boolean read, Instant readAt, Instant createdAt) {

        public static NotificationResponse from(Notification n) {
            return new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getMessage(), n.getActionUrl(),
                    n.isRead(), n.getReadAt(), n.getCreatedAt());
        }
    }

    public record UnreadCountResponse(long unread) {
    }

    public record AnnouncementRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 5000) String message,
            @Schema(description = "ALL, ROLE, BATCH or COURSE") @NotNull Announcement.Audience audience,
            @Schema(description = "Required when audience is ROLE, e.g. STUDENT or TRAINER") String targetRole,
            @Schema(description = "The batch or course id, when audience is BATCH or COURSE") Long targetId,
            @Schema(description = "Also email everyone it reaches") Boolean sendEmail,
            @Schema(description = "Hidden from the list after this moment") Instant expiresAt) {
    }

    @Schema(description = "Who it goes to cannot change: it has already been delivered.")
    public record AnnouncementUpdateRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 5000) String message,
            Instant expiresAt) {
    }

    public record AnnouncementResponse(Long id, String title, String message, String audience, String targetRole,
                                       Long targetId, boolean sendEmail, Instant expiresAt, boolean withdrawn,
                                       @Schema(description = "Shown to staff only") Integer recipientCount,
                                       Long createdBy, Instant createdAt, Instant updatedAt) {

        public static AnnouncementResponse from(Announcement a, boolean forStaff) {
            return new AnnouncementResponse(a.getId(), a.getTitle(), a.getMessage(), a.getAudience().name(),
                    a.getTargetRole(), a.getTargetId(), a.isSendEmail(), a.getExpiresAt(), a.isWithdrawn(),
                    forStaff ? a.getRecipientCount() : null, a.getCreatedBy(), a.getCreatedAt(), a.getUpdatedAt());
        }
    }
}
