package com.itilms.notification.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.common.dto.PageResponse;
import com.itilms.common.event.EventPublisher;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.ForbiddenOperationException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.common.security.AppPrincipal;
import com.itilms.common.security.Roles;
import com.itilms.common.security.SecurityUtils;
import com.itilms.notification.client.BatchClient;
import com.itilms.notification.dto.NotificationDtos.AnnouncementRequest;
import com.itilms.notification.dto.NotificationDtos.AnnouncementResponse;
import com.itilms.notification.dto.NotificationDtos.AnnouncementUpdateRequest;
import com.itilms.notification.entity.Announcement;
import com.itilms.notification.entity.Announcement.Audience;
import com.itilms.notification.repository.AnnouncementRepository;
import com.itilms.notification.repository.BatchMemberRepository;
import com.itilms.notification.repository.NotificationRepository;

import lombok.RequiredArgsConstructor;

/**
 * Announcements (Doc S16). Admins and coordinators address anyone; a trainer
 * addresses only a batch they teach.
 */
@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private static final String SERVICE_NAME = "notification-service";
    private static final Set<String> ROLES = Set.of(Roles.ADMIN, Roles.COORDINATOR, Roles.TRAINER,
            Roles.STUDENT, Roles.PLACEMENT, Roles.FINANCE);

    private final AnnouncementRepository announcements;
    private final BatchMemberRepository members;
    private final NotificationRepository notifications;
    private final AudienceResolver audiences;
    private final NotificationDispatcher dispatcher;
    private final BatchClient batches;
    private final EventPublisher events;

    /** Saved and delivered together: the notifications cannot outlive a failed save, or go missing after one. */
    @Transactional
    public AnnouncementResponse create(AnnouncementRequest request) {
        AppPrincipal me = SecurityUtils.requirePrincipal();
        String role = validateAudience(request);
        if (me.isTrainer()) {
            requireTeaches(request);
        }
        if (request.expiresAt() != null && !request.expiresAt().isAfter(Instant.now())) {
            throw new BusinessRuleException("The expiry time must be in the future.");
        }

        Announcement announcement = announcements.save(Announcement.builder()
                .title(request.title().trim())
                .message(request.message().trim())
                .audience(request.audience())
                .category(request.category() == null ? Announcement.Category.GENERAL : request.category())
                .targetRole(role)
                .targetId(request.audience() == Audience.BATCH || request.audience() == Audience.COURSE
                        ? request.targetId() : null)
                .sendEmail(Boolean.TRUE.equals(request.sendEmail()))
                .expiresAt(request.expiresAt())
                .build());

        Content content = new Content("ANNOUNCEMENT", announcement.getTitle(), announcement.getMessage(),
                "/announcements/" + announcement.getId());
        int reached = dispatcher.deliver(announcement.sourceEventId(), audiences.forAnnouncement(announcement),
                content, announcement.isSendEmail());
        announcement.setRecipientCount(reached);

        events.audit(SERVICE_NAME, "ANNOUNCEMENT_PUBLISHED", "Announcement", announcement.getId(), null,
                Map.of("audience", announcement.getAudience().name(), "recipients", reached));
        return AnnouncementResponse.from(announcement, true);
    }

    /** Wording and expiry only. The people it went to have already been told. */
    @Transactional
    public AnnouncementResponse update(Long id, AnnouncementUpdateRequest request) {
        Announcement announcement = requireEditable(id);
        announcement.setTitle(request.title().trim());
        announcement.setMessage(request.message().trim());
        announcement.setExpiresAt(request.expiresAt());
        return AnnouncementResponse.from(announcement, true);
    }

    /** Hides it and takes it off everyone's notification list, read or not. */
    @Transactional
    public AnnouncementResponse withdraw(Long id) {
        Announcement announcement = requireEditable(id);
        announcement.setWithdrawn(true);
        int removed = notifications.deleteBySourceEventId(announcement.sourceEventId());
        events.audit(SERVICE_NAME, "ANNOUNCEMENT_WITHDRAWN", "Announcement", id, null,
                Map.of("notificationsRemoved", removed));
        return AnnouncementResponse.from(announcement, true);
    }

    @Transactional(readOnly = true)
    public PageResponse<AnnouncementResponse> list(Pageable pageable) {
        AppPrincipal me = SecurityUtils.requirePrincipal();
        if (isStaff(me)) {
            return PageResponse.from(announcements.findAllByOrderByCreatedAtDescIdDesc(pageable),
                    a -> AnnouncementResponse.from(a, true));
        }
        return PageResponse.from(announcements.visibleTo(me.userId(), me.role(), Instant.now(), pageable),
                a -> AnnouncementResponse.from(a, me.userId().equals(a.getCreatedBy())));
    }

    @Transactional(readOnly = true)
    public AnnouncementResponse get(Long id) {
        AppPrincipal me = SecurityUtils.requirePrincipal();
        Announcement announcement = announcements.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Announcement", id));
        boolean mine = me.userId().equals(announcement.getCreatedBy());
        if (!isStaff(me) && !mine && !(announcement.isVisibleAt(Instant.now()) && addressedTo(announcement, me))) {
            throw new ResourceNotFoundException("Announcement", id);
        }
        return AnnouncementResponse.from(announcement, isStaff(me) || mine);
    }

    // -----------------------------------------------------------------

    boolean addressedTo(Announcement a, AppPrincipal me) {
        return switch (a.getAudience()) {
            case ALL -> true;
            case ROLE -> a.getTargetRole().equals(me.role());
            case BATCH -> members.existsByBatchIdAndUserIdAndActiveTrue(a.getTargetId(), me.userId());
            case COURSE -> members.existsByCourseIdAndUserIdAndActiveTrue(a.getTargetId(), me.userId());
        };
    }

    /** @return the normalised role for a ROLE announcement, otherwise null */
    static String validateAudience(AnnouncementRequest request) {
        return switch (request.audience()) {
            case ALL -> null;
            case ROLE -> {
                String role = request.targetRole() == null ? "" : request.targetRole().trim().toUpperCase();
                if (!ROLES.contains(role)) {
                    throw new BusinessRuleException("Choose which role to address: " + String.join(", ", ROLES.stream().sorted().toList()) + ".");
                }
                yield role;
            }
            case BATCH, COURSE -> {
                if (request.targetId() == null) {
                    throw new BusinessRuleException("Choose which " + request.audience().name().toLowerCase() + " to address.");
                }
                yield null;
            }
        };
    }

    private void requireTeaches(AnnouncementRequest request) {
        if (request.audience() != Audience.BATCH) {
            throw new ForbiddenOperationException("Trainers can make announcements to their own batches only.");
        }
        List<BatchClient.BatchSummary> mine = batches.myBatches();
        if (mine == null) {
            throw new BusinessRuleException("Your batches could not be checked just now. Please try again shortly.");
        }
        if (mine.stream().noneMatch(b -> request.targetId().equals(b.id()))) {
            throw new ForbiddenOperationException("You can make announcements only to batches you teach.");
        }
    }

    private Announcement requireEditable(Long id) {
        Announcement announcement = announcements.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Announcement", id));
        AppPrincipal me = SecurityUtils.requirePrincipal();
        if (!isStaff(me) && !me.userId().equals(announcement.getCreatedBy())) {
            throw new ForbiddenOperationException("Only the author or an administrator can change this announcement.");
        }
        if (announcement.isWithdrawn()) {
            throw new BusinessRuleException("This announcement has been withdrawn.");
        }
        return announcement;
    }

    private static boolean isStaff(AppPrincipal me) {
        return Roles.ADMIN.equals(me.role()) || Roles.COORDINATOR.equals(me.role());
    }
}
