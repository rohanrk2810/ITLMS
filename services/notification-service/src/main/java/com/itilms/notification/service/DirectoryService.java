package com.itilms.notification.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.common.event.EnrollmentCreatedEvent;
import com.itilms.common.event.UserCreatedEvent;
import com.itilms.common.event.UserStatusChangedEvent;
import com.itilms.common.security.AppPrincipal;
import com.itilms.notification.repository.BatchMemberRepository;
import com.itilms.notification.repository.RecipientRepository;

import lombok.RequiredArgsConstructor;

/**
 * Keeps the local copies of users and enrolments current. Every write is an
 * upsert, so a repeated or late event leaves the same result.
 */
@Service
@RequiredArgsConstructor
public class DirectoryService {

    private final RecipientRepository recipients;
    private final BatchMemberRepository members;

    @Transactional
    public void userChanged(UserCreatedEvent event) {
        recipients.upsertProfile(event.userId(), event.email(), event.fullName(), event.role());
    }

    @Transactional
    public void statusChanged(UserStatusChangedEvent event) {
        recipients.upsertStatus(event.userId(), event.email(), "ACTIVE".equals(event.newStatus()), event.occurredAt());
    }

    @Transactional
    public void enrolment(EnrollmentCreatedEvent event, boolean joined) {
        // A student admitted without a login has nothing to notify.
        if (event.userId() == null || event.batchId() == null || event.courseId() == null) {
            return;
        }
        members.upsert(event.batchId(), event.userId(), event.courseId(), joined, event.occurredAt());
    }

    /**
     * Anyone who signs in is known from then on, even if they were created
     * before this service existed and their USER_CREATED event is long gone.
     */
    @Transactional
    public void remember(AppPrincipal principal) {
        recipients.insertIfAbsent(principal.userId(), principal.email(), principal.fullName(), principal.role());
    }
}
