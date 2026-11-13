package com.itilms.batch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.itilms.batch.client.AdmissionClient;
import com.itilms.batch.client.CourseClient;
import com.itilms.batch.dto.request.BatchCourseRequests;
import com.itilms.batch.entity.Batch;
import com.itilms.batch.entity.BatchCourse;
import com.itilms.batch.entity.Enrollment;
import com.itilms.batch.entity.EnrollmentStatus;
import com.itilms.batch.repository.BatchCourseRepository;
import com.itilms.batch.repository.BatchRepository;
import com.itilms.batch.repository.EnrollmentRepository;
import com.itilms.common.event.EnrollmentCreatedEvent;
import com.itilms.common.event.EventPublisher;
import com.itilms.common.event.KafkaTopics;
import com.itilms.common.exception.BusinessRuleException;

/**
 * A batch holds more than one course: everyone placed in the whole batch has them all, at the batch fee, and a student
 * placed in one course alone does not get the others.
 */
class BatchCourseServiceTest {

    private static final long BATCH = 5L;
    private static final long JAVA = 1L;
    private static final long SQL = 2L;

    private final BatchRepository batches = mock(BatchRepository.class);
    private final BatchCourseRepository batchCourses = mock(BatchCourseRepository.class);
    private final EnrollmentRepository enrollments = mock(EnrollmentRepository.class);
    private final CourseClient courseClient = mock(CourseClient.class);
    private final AdmissionClient admission = mock(AdmissionClient.class);
    private final EventPublisher events = mock(EventPublisher.class);
    private final BatchCourseService service =
            new BatchCourseService(batches, batchCourses, enrollments, courseClient, admission, events);

    private final Batch batch = Batch.builder().id(BATCH).batchCode("JAP-1").courseId(JAVA).courseCode("JAVA")
            .courseTitle("Core Java").trainerId(11L).trainerName("Sir A").build();

    private Enrollment placement(long id, long student, boolean courseOnly) {
        return Enrollment.builder().id(id).studentId(student).userId(student + 100).studentName("S" + student)
                .courseId(JAVA).batchId(BATCH).courseOnly(courseOnly).status(EnrollmentStatus.ACTIVE).build();
    }

    private void sqlExists() {
        when(courseClient.get(SQL)).thenReturn(new CourseClient.CourseDetail(
                new CourseClient.CourseSummary(SQL, "SQL", "SQL", "PUBLISHED")));
    }

    @Test
    void addingACourseEnrolsEveryoneInTheWholeBatchAtNoFee() {
        when(batches.findById(BATCH)).thenReturn(Optional.of(batch));
        sqlExists();
        when(enrollments.findWholeBatchPlacements(BATCH, JAVA))
                .thenReturn(List.of(placement(1, 10, false), placement(2, 11, false)));
        when(enrollments.save(any())).thenAnswer(inv -> {
            Enrollment e = inv.getArgument(0);
            e.setId(100L + e.getStudentId());
            return e;
        });

        service.add(BATCH, new BatchCourseRequests.Add(SQL, null));

        ArgumentCaptor<Enrollment> saved = ArgumentCaptor.forClass(Enrollment.class);
        verify(enrollments, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).allSatisfy(e -> {
            assertThat(e.isIncluded()).isTrue();
            assertThat(e.getCourseId()).isEqualTo(SQL);
            assertThat(e.getBatchId()).isEqualTo(BATCH);
        });
        ArgumentCaptor<EnrollmentCreatedEvent> sent = ArgumentCaptor.forClass(EnrollmentCreatedEvent.class);
        verify(events, times(2)).publishAfterCommit(eq(KafkaTopics.ENROLLMENT_CREATED), sent.capture());
        assertThat(sent.getAllValues()).allSatisfy(event -> {
            assertThat(event.feeIncluded()).isTrue();
            assertThat(event.courseId()).isEqualTo(SQL);
        });
        verify(batchCourses).save(any(BatchCourse.class));
    }

    @Test
    void aCourseAlreadyInTheBatchCannotBeAddedAgain() {
        when(batches.findById(BATCH)).thenReturn(Optional.of(batch));

        assertThatThrownBy(() -> service.add(BATCH, new BatchCourseRequests.Add(JAVA, null)))
                .isInstanceOf(BusinessRuleException.class);

        when(batchCourses.existsByBatchIdAndCourseId(BATCH, SQL)).thenReturn(true);
        assertThatThrownBy(() -> service.add(BATCH, new BatchCourseRequests.Add(SQL, null)))
                .isInstanceOf(BusinessRuleException.class);
        verify(enrollments, never()).save(any());
    }

    @Test
    void theBatchsOwnCourseCannotBeTakenOut() {
        when(batches.findById(BATCH)).thenReturn(Optional.of(batch));
        assertThatThrownBy(() -> service.remove(BATCH, JAVA)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void takingACourseOutClosesTheStudentsLessonsForIt() {
        when(batches.findById(BATCH)).thenReturn(Optional.of(batch));
        BatchCourse added = BatchCourse.builder().batchId(BATCH).courseId(SQL).courseTitle("SQL").build();
        when(batchCourses.findByBatchIdAndCourseId(BATCH, SQL)).thenReturn(Optional.of(added));
        Enrollment included = Enrollment.builder().id(9L).studentId(10L).userId(110L).courseId(SQL).batchId(BATCH)
                .included(true).status(EnrollmentStatus.ACTIVE).build();
        when(enrollments.findByBatchIdAndCourseIdAndIncludedTrueAndStatus(BATCH, SQL, EnrollmentStatus.ACTIVE))
                .thenReturn(List.of(included));

        service.remove(BATCH, SQL);

        assertThat(included.getStatus()).isEqualTo(EnrollmentStatus.DROPPED);
        verify(events).publishAfterCommit(eq(KafkaTopics.ENROLLMENT_CLOSED), any(EnrollmentCreatedEvent.class));
        verify(batchCourses).delete(added);
    }

    @Test
    void onlyBatchesWithAFeeAndAFreeSeatAreOfferedForSale() {
        Batch roomy = Batch.builder().id(1L).batchCode("A").name("Java Placement").courseId(JAVA).courseTitle("Core Java")
                .capacity(10).fee(new java.math.BigDecimal("60000")).status(com.itilms.batch.entity.BatchStatus.PLANNED)
                .startDate(java.time.LocalDate.now()).startTime(java.time.LocalTime.of(10, 0))
                .endTime(java.time.LocalTime.of(12, 0)).build();
        Batch full = Batch.builder().id(2L).batchCode("B").courseId(JAVA).courseTitle("Core Java").capacity(2)
                .fee(new java.math.BigDecimal("50000")).status(com.itilms.batch.entity.BatchStatus.PLANNED)
                .startDate(java.time.LocalDate.now()).startTime(java.time.LocalTime.of(10, 0))
                .endTime(java.time.LocalTime.of(12, 0)).build();
        when(batches.findByStatusInAndFeeGreaterThanOrderByStartDateAsc(any(), any())).thenReturn(List.of(roomy, full));
        when(enrollments.countActiveGroupedByBatch(any()))
                .thenReturn(List.<Object[]>of(new Object[] {1L, 3L}, new Object[] {2L, 2L}));
        when(batchCourses.findByBatchIdIn(any())).thenReturn(List.of(
                BatchCourse.builder().batchId(1L).courseId(SQL).courseTitle("SQL").build()));

        var offered = service.openForSale();

        assertThat(offered).hasSize(1);
        assertThat(offered.get(0).id()).isEqualTo(1L);
        assertThat(offered.get(0).seatsLeft()).isEqualTo(7);
        assertThat(offered.get(0).courseTitles()).containsExactly("Core Java", "SQL");
    }

    @Test
    void aLectureOfAnAddedCourseIsTaughtByThatCoursesTrainer() {
        BatchCourse added = BatchCourse.builder().batchId(BATCH).courseId(SQL).courseTitle("SQL").trainerId(22L)
                .trainerUserId(122L).trainerName("Sir B").build();
        when(batchCourses.findByBatchIdAndCourseId(BATCH, SQL)).thenReturn(Optional.of(added));

        var own = service.teaching(batch, null);
        var sql = service.teaching(batch, SQL);

        assertThat(own.courseId()).isNull();
        assertThat(own.trainerId()).isEqualTo(11L);
        assertThat(sql.courseId()).isEqualTo(SQL);
        assertThat(sql.trainerId()).isEqualTo(22L);
        assertThatThrownBy(() -> service.teaching(batch, 99L)).isInstanceOf(BusinessRuleException.class);
    }
}
