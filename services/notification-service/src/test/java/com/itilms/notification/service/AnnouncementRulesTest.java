package com.itilms.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.itilms.common.event.EventPublisher;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.ForbiddenOperationException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.common.security.AppPrincipal;
import com.itilms.notification.client.BatchClient;
import com.itilms.notification.dto.NotificationDtos;
import com.itilms.notification.dto.NotificationDtos.AnnouncementRequest;
import com.itilms.notification.entity.Announcement;
import com.itilms.notification.entity.Announcement.Audience;
import com.itilms.notification.repository.AnnouncementRepository;
import com.itilms.notification.repository.BatchMemberRepository;
import com.itilms.notification.repository.NotificationRepository;

class AnnouncementRulesTest {

    private final AnnouncementRepository announcements = mock(AnnouncementRepository.class);
    private final BatchMemberRepository members = mock(BatchMemberRepository.class);
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final NotificationDispatcher dispatcher = mock(NotificationDispatcher.class);
    private final BatchClient batches = mock(BatchClient.class);
    private final AnnouncementService service = new AnnouncementService(announcements, members, notifications,
            mock(AudienceResolver.class), dispatcher, batches, mock(EventPublisher.class));

    private static void signIn(Long userId, String role) {
        AppPrincipal p = new AppPrincipal(userId, "u@x.in", "User", role, 50L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(p, null, p.authorities()));
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private static AnnouncementRequest request(Audience audience, String role, Long targetId) {
        return new AnnouncementRequest("Holiday", "Closed on Monday", audience, role, targetId, false, null, null);
    }

    @Test
    @DisplayName("an announcement with no category is a general one, and the response says so")
    void categoryDefaultsToGeneral() {
        Announcement announcement = Announcement.builder().title("t").message("m").audience(Audience.ALL).build();

        assertThat(announcement.getCategory()).isEqualTo(Announcement.Category.GENERAL);
        assertThat(NotificationDtos.AnnouncementResponse.from(announcement, false).category()).isEqualTo("GENERAL");
    }

    @Test
    @DisplayName("A role announcement must name a real role; a batch or course one must name which")
    void audienceMustBeComplete() {
        assertThatThrownBy(() -> AnnouncementService.validateAudience(request(Audience.ROLE, "PARENT", null)))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> AnnouncementService.validateAudience(request(Audience.BATCH, null, null)))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(AnnouncementService.validateAudience(request(Audience.ROLE, " student ", null))).isEqualTo("STUDENT");
    }

    @Test
    @DisplayName("A trainer cannot announce to the whole institute")
    void trainerNotToEveryone() {
        signIn(8L, "TRAINER");
        assertThatThrownBy(() -> service.create(request(Audience.ALL, null, null)))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    @DisplayName("A trainer cannot announce to a batch they do not teach")
    void trainerOnlyOwnBatch() {
        signIn(8L, "TRAINER");
        when(batches.myBatches()).thenReturn(List.of(new BatchClient.BatchSummary(3L, 1L)));

        assertThatThrownBy(() -> service.create(request(Audience.BATCH, null, 4L)))
                .isInstanceOf(ForbiddenOperationException.class);
        verify(announcements, never()).save(any());
    }

    @Test
    @DisplayName("If batch-service cannot confirm it, the trainer is refused rather than trusted")
    void trainerCheckFailsClosed() {
        signIn(8L, "TRAINER");
        when(batches.myBatches()).thenReturn(null);

        assertThatThrownBy(() -> service.create(request(Audience.BATCH, null, 3L)))
                .isInstanceOf(BusinessRuleException.class);
        verify(announcements, never()).save(any());
    }

    @Test
    @DisplayName("Withdrawing takes it off everyone's notification list")
    void withdrawRemovesNotifications() {
        signIn(1L, "ADMIN");
        Announcement a = Announcement.builder().id(9L).title("t").message("m").audience(Audience.ALL).build();
        when(announcements.findById(9L)).thenReturn(Optional.of(a));

        service.withdraw(9L);

        assertThat(a.isWithdrawn()).isTrue();
        verify(notifications).deleteBySourceEventId("announcement-9");
    }

    @Test
    @DisplayName("A student cannot open an announcement meant for another batch")
    void notAddressed() {
        signIn(20L, "STUDENT");
        Announcement a = Announcement.builder().id(9L).title("t").message("m")
                .audience(Audience.BATCH).targetId(3L).build();
        when(announcements.findById(9L)).thenReturn(Optional.of(a));
        when(members.existsByBatchIdAndUserIdAndActiveTrue(3L, 20L)).thenReturn(false);

        assertThatThrownBy(() -> service.get(9L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Only the author or an administrator may edit")
    void onlyAuthorEdits() {
        signIn(8L, "TRAINER");
        Announcement a = Announcement.builder().id(9L).title("t").message("m").audience(Audience.ALL).build();
        a.setCreatedBy(1L);
        when(announcements.findById(9L)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.withdraw(9L)).isInstanceOf(ForbiddenOperationException.class);
    }
}
