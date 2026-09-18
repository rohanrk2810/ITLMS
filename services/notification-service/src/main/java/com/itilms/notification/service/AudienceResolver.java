package com.itilms.notification.service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.common.event.NotificationRequestedEvent;
import com.itilms.common.security.Roles;
import com.itilms.notification.entity.Announcement;
import com.itilms.notification.repository.BatchMemberRepository;
import com.itilms.notification.repository.RecipientRepository;

import lombok.RequiredArgsConstructor;

/**
 * Expands "batch 12" or "every finance user" into user ids, from the local
 * copies of users and enrolments. No call to another service is needed, so a
 * notification still goes out while identity-service or batch-service is down.
 */
@Component
@RequiredArgsConstructor
public class AudienceResolver {

    private final RecipientRepository recipients;
    private final BatchMemberRepository members;

    /** Named users, plus a batch, plus a role - whichever the request set. */
    @Transactional(readOnly = true)
    public Set<Long> forRequest(NotificationRequestedEvent event) {
        Set<Long> ids = new LinkedHashSet<>();
        if (event.recipientUserIds() != null) {
            event.recipientUserIds().stream().filter(id -> id != null).forEach(ids::add);
        }
        if (event.batchId() != null) {
            ids.addAll(members.currentMembersOfBatch(event.batchId()));
        }
        if (event.role() != null && !event.role().isBlank()) {
            ids.addAll(recipients.activeUserIdsWithRole(event.role().trim().toUpperCase()));
        }
        return ids;
    }

    /** A job with no course restriction is open to every student. */
    @Transactional(readOnly = true)
    public Set<Long> forJob(Collection<Long> eligibleCourseIds) {
        List<Long> ids = eligibleCourseIds == null || eligibleCourseIds.isEmpty()
                ? recipients.activeUserIdsWithRole(Roles.STUDENT)
                : members.everMembersOfCourses(eligibleCourseIds);
        return new LinkedHashSet<>(ids);
    }

    @Transactional(readOnly = true)
    public Set<Long> forAnnouncement(Announcement announcement) {
        List<Long> ids = switch (announcement.getAudience()) {
            case ALL -> recipients.activeUserIds();
            case ROLE -> recipients.activeUserIdsWithRole(announcement.getTargetRole());
            case BATCH -> members.currentMembersOfBatch(announcement.getTargetId());
            case COURSE -> members.currentMembersOfCourse(announcement.getTargetId());
        };
        return new LinkedHashSet<>(ids);
    }
}
