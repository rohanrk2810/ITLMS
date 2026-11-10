package com.itilms.batch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.itilms.batch.dto.request.ValidityDtos.ExtendAccessRequest;
import com.itilms.batch.dto.request.ValidityDtos.ExtendBatchRequest;
import com.itilms.batch.entity.Batch;
import com.itilms.batch.entity.BatchStatus;
import com.itilms.batch.entity.Enrollment;
import com.itilms.batch.entity.EnrollmentStatus;
import com.itilms.batch.repository.BatchRepository;
import com.itilms.batch.repository.EnrollmentRepository;
import com.itilms.common.event.EventPublisher;
import com.itilms.common.event.KafkaTopics;
import com.itilms.common.exception.BusinessRuleException;

/**
 * A course that is not finished by its planned end date: the administrator extends the batch, or one student's access,
 * and the people concerned are told. Nothing is locked for a student who has no access date.
 */
class BatchValidityServiceTest {

    private static final LocalDate TODAY = LocalDate.now();

    private final BatchRepository batches = mock(BatchRepository.class);
    private final EnrollmentRepository enrollments = mock(EnrollmentRepository.class);
    private final EventPublisher events = mock(EventPublisher.class);
    private final BatchValidityService service = new BatchValidityService(batches, enrollments, events);

    private Batch batch(BatchStatus status, LocalDate end) {
        Batch batch = Batch.builder().batchCode("JFS-1").courseId(9L).startDate(TODAY.minusMonths(3)).endDate(end)
                .status(status).trainerUserId(55L).build();
        batch.setId(3L);
        when(batches.findById(3L)).thenReturn(Optional.of(batch));
        return batch;
    }

    private Enrollment enrollment(long id, EnrollmentStatus status, LocalDate until) {
        Enrollment e = Enrollment.builder().studentId(30L + id).userId(70L + id).courseId(9L).batchId(3L)
                .studentCode("STU-" + id).studentName("Student " + id).status(status).accessUntil(until).build();
        e.setId(id);
        when(enrollments.findById(id)).thenReturn(Optional.of(e));
        return e;
    }

    // -----------------------------------------------------------------
    // A whole batch
    // -----------------------------------------------------------------

    @Test
    void extendingAFinishedBatchRunsItAgainAndTellsStudentsAndTrainer() {
        Batch batch = batch(BatchStatus.COMPLETED, TODAY.minusDays(2));
        when(enrollments.findByBatchId(3L)).thenReturn(List.of());

        var result = service.extendBatch(3L, new ExtendBatchRequest(TODAY.plusDays(30), "  Syllabus not finished "));

        assertThat(batch.getEndDate()).isEqualTo(TODAY.plusDays(30));
        assertThat(batch.getStatus()).isEqualTo(BatchStatus.ONGOING);
        assertThat(result.status()).isEqualTo("ONGOING");
        verify(batches).save(batch);
        verify(events).notifyBatch(eq(3L), any(), any(), any(), any());
        verify(events).notifyUsers(eq(List.of(55L)), any(), any(), any(), any());
        verify(events).audit(any(), eq("BATCH_EXTENDED"), any(), eq(3L), any(), any());
    }

    @Test
    void theNewEndDateMustBeLater_andACancelledBatchCannotBeExtended() {
        batch(BatchStatus.ONGOING, TODAY.plusDays(10));
        assertThatThrownBy(() -> service.extendBatch(3L, new ExtendBatchRequest(TODAY.plusDays(10), "x")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("after");
        assertThatThrownBy(() -> service.extendBatch(3L, new ExtendBatchRequest(TODAY.plusDays(5), "x")))
                .isInstanceOf(BusinessRuleException.class);

        batch(BatchStatus.CANCELLED, TODAY);
        assertThatThrownBy(() -> service.extendBatch(3L, new ExtendBatchRequest(TODAY.plusDays(40), "x")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("cancelled");
    }

    @Test
    void studentsWhoseAccessWasTheBatchEndDateFollowIt_andOtherDatesAreLeftAlone() {
        LocalDate oldEnd = TODAY.minusDays(1);
        batch(BatchStatus.COMPLETED, oldEnd);
        Enrollment following = enrollment(1, EnrollmentStatus.EXPIRED, oldEnd);
        Enrollment individual = enrollment(2, EnrollmentStatus.ACTIVE, TODAY.plusDays(200));
        Enrollment unlimited = enrollment(3, EnrollmentStatus.ACTIVE, null);
        Enrollment dropped = enrollment(4, EnrollmentStatus.DROPPED, oldEnd);
        when(enrollments.findByBatchId(3L)).thenReturn(List.of(following, individual, unlimited, dropped));

        var result = service.extendBatch(3L, new ExtendBatchRequest(TODAY.plusDays(30), "More time"));

        assertThat(result.studentsExtended()).isEqualTo(1);
        assertThat(following.getAccessUntil()).isEqualTo(TODAY.plusDays(30));
        assertThat(following.getStatus()).isEqualTo(EnrollmentStatus.ACTIVE);
        assertThat(individual.getAccessUntil()).isEqualTo(TODAY.plusDays(200));
        assertThat(unlimited.getAccessUntil()).isNull();
        assertThat(dropped.getStatus()).isEqualTo(EnrollmentStatus.DROPPED);
        verify(events).publishAfterCommit(eq(KafkaTopics.ENROLLMENT_CREATED), any());
    }

    // -----------------------------------------------------------------
    // One student
    // -----------------------------------------------------------------

    @Test
    void extendingAnExpiredStudentOpensTheCourseAgainEverywhere() {
        Enrollment expired = enrollment(1, EnrollmentStatus.EXPIRED, TODAY.minusDays(3));
        when(enrollments.findActiveForStudent(31L)).thenReturn(List.of());

        var response = service.extendAccess(1L, new ExtendAccessRequest(TODAY.plusDays(45), "Missed classes for illness"));

        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.accessUntil()).isEqualTo(TODAY.plusDays(45));
        assertThat(expired.getStatus()).isEqualTo(EnrollmentStatus.ACTIVE);
        verify(events).publishAfterCommit(eq(KafkaTopics.ENROLLMENT_CREATED), any());
        verify(events).notifyUsers(eq(List.of(71L)), any(), any(), any(), any());
        verify(events).audit(any(), eq("ACCESS_EXTENDED"), any(), eq(1L), any(), any());
    }

    @Test
    void anActiveStudentWithNoLimitCanBeGivenADate_withoutAnyReopeningEvent() {
        Enrollment active = enrollment(2, EnrollmentStatus.ACTIVE, null);

        service.extendAccess(2L, new ExtendAccessRequest(TODAY.plusDays(90), "Fixed term"));

        assertThat(active.getAccessUntil()).isEqualTo(TODAY.plusDays(90));
        verify(events, never()).publishAfterCommit(any(), any());
    }

    @Test
    void aDroppedOrTransferredStudentCannotBeExtended_norToADateInThePast() {
        enrollment(3, EnrollmentStatus.DROPPED, null);
        assertThatThrownBy(() -> service.extendAccess(3L, new ExtendAccessRequest(TODAY.plusDays(5), "x")))
                .isInstanceOf(BusinessRuleException.class);

        enrollment(4, EnrollmentStatus.ACTIVE, null);
        assertThatThrownBy(() -> service.extendAccess(4L, new ExtendAccessRequest(TODAY.minusDays(1), "x")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("past");
    }

    @Test
    void aStudentAlreadyOnTheCourseInAnotherBatchCannotBeReopenedAsASecondOne() {
        enrollment(1, EnrollmentStatus.EXPIRED, TODAY.minusDays(3));
        Enrollment other = enrollment(9, EnrollmentStatus.ACTIVE, null);
        other.setStudentId(31L);
        when(enrollments.findActiveForStudent(31L)).thenReturn(List.of(other));

        assertThatThrownBy(() -> service.extendAccess(1L, new ExtendAccessRequest(TODAY.plusDays(5), "x")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("another batch");
    }

    // -----------------------------------------------------------------
    // The nightly sweep
    // -----------------------------------------------------------------

    @Test
    void theNightlySweepClosesOnlyEnrolmentsWhoseDatePassed_andTellsThem() {
        Enrollment late = enrollment(1, EnrollmentStatus.ACTIVE, TODAY.minusDays(1));
        when(enrollments.findByStatusAndAccessUntilBefore(EnrollmentStatus.ACTIVE, TODAY)).thenReturn(List.of(late));

        int closed = service.expireOverdue(TODAY);

        assertThat(closed).isEqualTo(1);
        assertThat(late.getStatus()).isEqualTo(EnrollmentStatus.EXPIRED);
        verify(events).publishAfterCommit(eq(KafkaTopics.ENROLLMENT_CLOSED), any());
        verify(events).notifyUsers(eq(List.of(71L)), any(), any(), any(), any());
    }

    @Test
    void whenNobodyIsOverdueNothingHappens() {
        when(enrollments.findByStatusAndAccessUntilBefore(EnrollmentStatus.ACTIVE, TODAY)).thenReturn(List.of());

        assertThat(service.expireOverdue(TODAY)).isZero();
        verify(events, never()).publishAfterCommit(any(), any());
    }
}
