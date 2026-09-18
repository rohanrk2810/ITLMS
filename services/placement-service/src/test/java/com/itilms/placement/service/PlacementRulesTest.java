package com.itilms.placement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.itilms.placement.client.AssessmentClient;
import com.itilms.placement.client.BatchClient;
import com.itilms.placement.client.CertificateClient;
import com.itilms.placement.dto.request.JobRequest;
import com.itilms.placement.entity.ApplicationStage;
import com.itilms.placement.entity.JobOpening;

class PlacementRulesTest {

    @Nested
    @DisplayName("Moving through the pipeline (Doc S7.4)")
    class Pipeline {

        @Test
        @DisplayName("The usual path is allowed, round after round")
        void usualPath() {
            assertThat(ApplicationStage.APPLIED.canMoveTo(ApplicationStage.SHORTLISTED)).isTrue();
            assertThat(ApplicationStage.SHORTLISTED.canMoveTo(ApplicationStage.INTERVIEW)).isTrue();
            assertThat(ApplicationStage.INTERVIEW.canMoveTo(ApplicationStage.INTERVIEW)).isTrue();
            assertThat(ApplicationStage.INTERVIEW.canMoveTo(ApplicationStage.SELECTED)).isTrue();
        }

        @Test
        @DisplayName("A decision is final - a rejected candidate cannot be marked selected by a slip")
        void decisionsAreFinal() {
            assertThat(ApplicationStage.REJECTED.allowedNext()).isEmpty();
            assertThat(ApplicationStage.SELECTED.allowedNext()).isEmpty();
            assertThat(ApplicationStage.WITHDRAWN.allowedNext()).isEmpty();
        }

        @Test
        @DisplayName("Nobody is selected without being shortlisted first")
        void noShortcut() {
            assertThat(ApplicationStage.APPLIED.canMoveTo(ApplicationStage.SELECTED)).isFalse();
        }
    }

    @Nested
    @DisplayName("Who may apply")
    class Eligibility {

        private final BatchClient batches = mock(BatchClient.class);
        private final CertificateClient certificates = mock(CertificateClient.class);
        private final AssessmentClient assessment = mock(AssessmentClient.class);
        private final EligibilityChecker checker = new EligibilityChecker(batches, certificates, assessment);

        private JobOpening job(Set<Long> courses, boolean certificate, Integer minAttendance, Integer minScore) {
            return JobOpening.builder().id(1L).companyId(1L).title("Java developer")
                    .eligibleCourseIds(new java.util.LinkedHashSet<>(courses)).requireCertificate(certificate)
                    .minAttendancePercent(minAttendance).minScorePercent(minScore).build();
        }

        private void studying(long courseId, long batchId) {
            when(batches.myBatches()).thenReturn(List.of(new BatchClient.BatchSummary(batchId, courseId, "Java", "ONGOING")));
        }

        private void certified(long courseId, long batchId) {
            when(certificates.myCertificates()).thenReturn(List.of(new CertificateClient.Certificate(courseId, batchId, "ISSUED")));
        }

        @Test
        @DisplayName("A graduate in no batch qualifies through their certificate")
        void graduateQualifies() {
            when(batches.myBatches()).thenReturn(List.of());
            certified(5L, 50L);

            var result = checker.check(job(Set.of(5L), true, null, null), checker.snapshot(3L));
            assertThat(result.eligible()).isTrue();
            assertThat(result.batchId()).isEqualTo(50L);
        }

        @Test
        @DisplayName("A job needing a certificate refuses a student still studying")
        void certificateRequired() {
            studying(5L, 51L);
            when(certificates.myCertificates()).thenReturn(List.of());

            var result = checker.check(job(Set.of(5L), true, null, null), checker.snapshot(3L));
            assertThat(result.eligible()).isFalse();
        }

        @Test
        @DisplayName("Students of other courses are refused")
        void otherCourse() {
            studying(6L, 60L);
            when(certificates.myCertificates()).thenReturn(List.of());

            assertThat(checker.check(job(Set.of(5L), false, null, null), checker.snapshot(3L)).eligible()).isFalse();
        }

        @Test
        @DisplayName("Attendance and score minimums are enforced, with a reason for each")
        void minimumsEnforced() {
            studying(5L, 51L);
            when(certificates.myCertificates()).thenReturn(List.of());
            when(batches.attendance(3L, 51L)).thenReturn(new BatchClient.Attendance(12, 20, BigDecimal.valueOf(60)));
            when(assessment.completion(3L, 5L, 51L)).thenReturn(new AssessmentClient.Completion(2, 55));

            var result = checker.check(job(Set.of(), false, 75, 60), checker.snapshot(3L));
            assertThat(result.eligible()).isFalse();
            assertThat(result.reasons()).hasSize(2);
        }

        @Test
        @DisplayName("When records cannot be checked, the answer is no - with a reason that says so")
        void unavailable() {
            when(batches.myBatches()).thenReturn(null);
            when(certificates.myCertificates()).thenReturn(null);

            var result = checker.check(job(Set.of(), false, null, null), checker.snapshot(3L));
            assertThat(result.eligible()).isFalse();
            assertThat(result.reasons()).singleElement().asString().contains("could not be checked");
        }
    }

    @Nested
    @DisplayName("Editing a job once students have applied")
    class Tightening {

        private final JobOpening job = JobOpening.builder().eligibleCourseIds(new java.util.LinkedHashSet<>(Set.of(5L, 6L)))
                .minAttendancePercent(60).build();

        private JobRequest request(Set<Long> courses, Boolean certificate, Integer attendance) {
            return new JobRequest(1L, "t", null, null, null, null, null, null, courses, certificate, attendance, null, null);
        }

        @Test
        @DisplayName("Relaxing is fine: more courses, lower attendance")
        void relaxing() {
            assertThat(JobService.tightensRules(job, request(Set.of(5L, 6L, 7L), false, 50))).isFalse();
        }

        @Test
        @DisplayName("Tightening is refused: fewer courses, a new certificate rule, higher attendance")
        void tightening() {
            assertThat(JobService.tightensRules(job, request(Set.of(5L), false, 60))).isTrue();
            assertThat(JobService.tightensRules(job, request(Set.of(5L, 6L), true, 60))).isTrue();
            assertThat(JobService.tightensRules(job, request(Set.of(5L, 6L), false, 70))).isTrue();
        }
    }
}
