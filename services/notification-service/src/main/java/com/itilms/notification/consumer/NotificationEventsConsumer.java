package com.itilms.notification.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.itilms.common.event.EnrollmentCreatedEvent;
import com.itilms.common.event.JobPostedEvent;
import com.itilms.common.event.KafkaTopics;
import com.itilms.common.event.NotificationRequestedEvent;
import com.itilms.common.event.UserCreatedEvent;
import com.itilms.common.event.UserStatusChangedEvent;
import com.itilms.notification.service.DirectoryService;
import com.itilms.notification.service.NotificationIntake;

import lombok.RequiredArgsConstructor;

/** Everything this service hears from the others. */
@Component
@RequiredArgsConstructor
public class NotificationEventsConsumer {

    private static final String GROUP = "notification-service";

    private final NotificationIntake intake;
    private final DirectoryService directory;

    @KafkaListener(topics = KafkaTopics.NOTIFICATION_REQUESTED, groupId = GROUP)
    public void onNotificationRequested(NotificationRequestedEvent event) {
        intake.handle(event);
    }

    @KafkaListener(topics = KafkaTopics.JOB_POSTED, groupId = GROUP)
    public void onJobPosted(JobPostedEvent event) {
        intake.handle(event);
    }

    @KafkaListener(topics = {KafkaTopics.USER_CREATED, KafkaTopics.USER_UPDATED}, groupId = GROUP)
    public void onUserChanged(UserCreatedEvent event) {
        directory.userChanged(event);
    }

    @KafkaListener(topics = KafkaTopics.USER_STATUS_CHANGED, groupId = GROUP)
    public void onUserStatusChanged(UserStatusChangedEvent event) {
        directory.statusChanged(event);
    }

    @KafkaListener(topics = KafkaTopics.ENROLLMENT_CREATED, groupId = GROUP)
    public void onEnrolled(EnrollmentCreatedEvent event) {
        directory.enrolment(event, true);
    }

    @KafkaListener(topics = KafkaTopics.ENROLLMENT_CLOSED, groupId = GROUP)
    public void onEnrollmentClosed(EnrollmentCreatedEvent event) {
        directory.enrolment(event, false);
    }
}
