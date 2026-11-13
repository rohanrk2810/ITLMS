package com.itilms.batch.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.itilms.batch.client.AdmissionClient;
import com.itilms.batch.client.CourseClient;
import com.itilms.batch.repository.BatchRepository;
import com.itilms.batch.repository.BatchTrainerRepository;
import com.itilms.batch.repository.EnrollmentRepository;
import com.itilms.common.event.EventPublisher;

/** Whether a student has been placed in a batch yet decides how much of the portal they see. */
class BatchPlacedTest {

    private final EnrollmentRepository enrollments = mock(EnrollmentRepository.class);
    private final BatchServiceImpl service = new BatchServiceImpl(mock(BatchRepository.class),
            mock(BatchTrainerRepository.class), enrollments, mock(com.itilms.batch.repository.BatchCourseRepository.class),
            mock(com.itilms.batch.service.BatchCourseService.class), mock(CourseClient.class), mock(AdmissionClient.class),
            mock(EventPublisher.class));

    @Test
    @DisplayName("A student who has never been enrolled is not placed")
    void newStudentIsNotPlaced() {
        when(enrollments.existsByStudentId(10L)).thenReturn(false);

        assertThat(service.isPlaced(10L)).isFalse();
    }

    @Test
    @DisplayName("A student with any enrolment is placed, even after the batch has ended")
    void enrolledStudentIsPlaced() {
        when(enrollments.existsByStudentId(10L)).thenReturn(true);

        assertThat(service.isPlaced(10L)).isTrue();
    }

    @Test
    @DisplayName("An account with no student profile yet is not placed, and nothing is looked up")
    void noProfileIsNotPlaced() {
        assertThat(service.isPlaced(null)).isFalse();
    }
}
