package com.itilms.batch.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.itilms.batch.client.AdmissionClient;
import com.itilms.batch.client.CourseClient;
import com.itilms.batch.entity.Batch;
import com.itilms.batch.entity.BatchMode;
import com.itilms.batch.entity.BatchStatus;
import com.itilms.batch.entity.Enrollment;
import com.itilms.batch.entity.EnrollmentStatus;
import com.itilms.batch.repository.BatchRepository;
import com.itilms.batch.repository.BatchTrainerRepository;
import com.itilms.batch.repository.EnrollmentRepository;
import com.itilms.common.event.EventPublisher;

/** A student's enrolments as the progress report reads them: newest first, with the batch's details beside each. */
class BatchEnrollmentsOfTest {

    @Test
    @DisplayName("Every enrolment comes back with its batch's code, course, trainer and dates, newest first")
    void enrollmentsWithBatchDetails() {
        BatchRepository batches = mock(BatchRepository.class);
        EnrollmentRepository enrollments = mock(EnrollmentRepository.class);
        BatchServiceImpl service = new BatchServiceImpl(batches, mock(BatchTrainerRepository.class), enrollments,
                mock(CourseClient.class), mock(AdmissionClient.class), mock(EventPublisher.class));

        Enrollment old = Enrollment.builder().id(1L).studentId(10L).batchId(5L).courseId(3L)
                .status(EnrollmentStatus.COMPLETED).enrolledAt(Instant.parse("2026-01-01T00:00:00Z")).build();
        Enrollment current = Enrollment.builder().id(2L).studentId(10L).batchId(6L).courseId(4L)
                .status(EnrollmentStatus.ACTIVE).enrolledAt(Instant.parse("2026-06-01T00:00:00Z")).build();
        when(enrollments.findByStudentId(10L)).thenReturn(List.of(old, current));
        when(batches.findAllById(any())).thenReturn(List.of(
                Batch.builder().id(5L).batchCode("JFS-01").name("Java morning").courseId(3L).courseTitle("Java")
                        .trainerName("Rohan").mode(BatchMode.OFFLINE).status(BatchStatus.COMPLETED)
                        .startDate(LocalDate.of(2026, 1, 5)).endDate(LocalDate.of(2026, 5, 5)).build(),
                Batch.builder().id(6L).batchCode("WEB-02").name("Web evening").courseId(4L).courseTitle("Web")
                        .trainerName("Asha").mode(BatchMode.ONLINE).status(BatchStatus.ONGOING)
                        .startDate(LocalDate.of(2026, 6, 5)).build()));

        var result = service.enrollmentsOf(10L);

        assertThat(result).extracting(r -> r.batchCode()).containsExactly("WEB-02", "JFS-01");
        assertThat(result.get(0).status()).isEqualTo("ACTIVE");
        assertThat(result.get(0).courseTitle()).isEqualTo("Web");
        assertThat(result.get(0).trainerName()).isEqualTo("Asha");
        assertThat(result.get(0).mode()).isEqualTo("ONLINE");
        assertThat(result.get(1).status()).isEqualTo("COMPLETED");
        assertThat(result.get(1).endDate()).isEqualTo(LocalDate.of(2026, 5, 5));
    }

    @Test
    @DisplayName("An enrolment whose batch cannot be found still comes back, without the batch details")
    void missingBatchDoesNotHideTheEnrolment() {
        BatchRepository batches = mock(BatchRepository.class);
        EnrollmentRepository enrollments = mock(EnrollmentRepository.class);
        BatchServiceImpl service = new BatchServiceImpl(batches, mock(BatchTrainerRepository.class), enrollments,
                mock(CourseClient.class), mock(AdmissionClient.class), mock(EventPublisher.class));
        when(enrollments.findByStudentId(10L)).thenReturn(List.of(Enrollment.builder().id(1L).studentId(10L)
                .batchId(99L).courseId(3L).status(EnrollmentStatus.ACTIVE).enrolledAt(Instant.now()).build()));
        when(batches.findAllById(any())).thenReturn(List.of());

        var result = service.enrollmentsOf(10L);

        assertThat(result).singleElement().satisfies(r -> {
            assertThat(r.batchId()).isEqualTo(99L);
            assertThat(r.batchCode()).isNull();
            assertThat(r.courseId()).isEqualTo(3L);
        });
    }
}
