package com.itilms.placement.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.itilms.placement.client.AssessmentClient;
import com.itilms.placement.client.BatchClient;
import com.itilms.placement.client.CertificateClient;
import com.itilms.placement.entity.JobOpening;

import lombok.RequiredArgsConstructor;

/**
 * Whether a student may apply for a job (Doc S7.4 "Eligibility Rules
 * Configured").
 *
 * <p>A student qualifies through a course, found either among their current
 * batches or among their certificates - a graduate is in no batch any more, and
 * is exactly who most jobs are for. The job's other rules are then checked in
 * that course: a certificate if required, attendance, and average test score.
 *
 * <p>Anything that could not be checked counts against eligibility, with a
 * reason that says so. A student told "attendance could not be checked, try
 * again" can act on it; one wrongly allowed through wastes a recruiter's
 * interview slot.
 *
 * <p>Work per student is loaded once through {@link #snapshot}, so listing
 * twenty jobs does not ask batch-service twenty times.
 */
@Component
@RequiredArgsConstructor
public class EligibilityChecker {

    private final BatchClient batchClient;
    private final CertificateClient certificateClient;
    private final AssessmentClient assessmentClient;

    /** The outcome for one job. */
    public record Result(boolean eligible, Long courseId, Long batchId, List<String> reasons) {
    }

    /** What the student has studied, loaded once per request. */
    public Snapshot snapshot(Long studentId) {
        return new Snapshot(studentId, batchClient.myBatches(), certificateClient.myCertificates());
    }

    public Result check(JobOpening job, Snapshot student) {
        if (student.batches == null && student.certificates == null) {
            return refused("Your course records could not be checked. Please try again shortly.");
        }

        Candidate qualifying = null;
        for (Candidate candidate : student.candidates()) {
            boolean courseFits = job.getEligibleCourseIds().isEmpty()
                    || job.getEligibleCourseIds().contains(candidate.courseId());
            boolean certificateFits = !job.isRequireCertificate() || candidate.certified();
            if (courseFits && certificateFits) {
                qualifying = candidate;
                break;
            }
        }
        if (qualifying == null) {
            if (job.isRequireCertificate() && student.certificates == null) {
                return refused("Your certificates could not be checked. Please try again shortly.");
            }
            return refused(job.isRequireCertificate()
                    ? "This job needs a certificate in one of the courses it is open to."
                    : "This job is open to students of other courses.");
        }

        List<String> reasons = new ArrayList<>();
        if (job.getMinAttendancePercent() != null) {
            BatchClient.Attendance attendance = student.attendance(qualifying.batchId(), batchClient);
            if (attendance == null) {
                reasons.add("Your attendance could not be checked. Please try again shortly.");
            } else if (attendance.totalSessions() > 0) {
                BigDecimal percent = attendance.attendancePercent() == null ? BigDecimal.ZERO : attendance.attendancePercent();
                if (percent.compareTo(BigDecimal.valueOf(job.getMinAttendancePercent())) < 0) {
                    reasons.add("Your attendance of %s%% is below the %d%% this job requires."
                            .formatted(percent.stripTrailingZeros().toPlainString(), job.getMinAttendancePercent()));
                }
            }
        }
        if (job.getMinScorePercent() != null) {
            AssessmentClient.Completion results = student.results(qualifying.courseId(), qualifying.batchId(), assessmentClient);
            if (results == null) {
                reasons.add("Your test results could not be checked. Please try again shortly.");
            } else if (results.testsRequired() > 0) {
                if (results.averageTestPercent() == null) {
                    reasons.add("Some of your test results have not been released yet.");
                } else if (results.averageTestPercent() < job.getMinScorePercent()) {
                    reasons.add("Your average test score of %d%% is below the %d%% this job requires."
                            .formatted(results.averageTestPercent(), job.getMinScorePercent()));
                }
            }
        }
        return new Result(reasons.isEmpty(), qualifying.courseId(), qualifying.batchId(), List.copyOf(reasons));
    }

    private static Result refused(String reason) {
        return new Result(false, null, null, List.of(reason));
    }

    // -----------------------------------------------------------------

    /** A course the student can qualify through, and the batch they studied it in. */
    record Candidate(Long courseId, Long batchId, boolean certified) {
    }

    /** One student's records, with the per-batch lookups remembered for the request. */
    public static final class Snapshot {

        private final Long studentId;
        private final List<BatchClient.BatchSummary> batches;
        private final List<CertificateClient.Certificate> certificates;
        private final Map<Long, BatchClient.Attendance> attendanceByBatch = new HashMap<>();
        private final Map<Long, AssessmentClient.Completion> resultsByBatch = new HashMap<>();

        Snapshot(Long studentId, List<BatchClient.BatchSummary> batches,
                 List<CertificateClient.Certificate> certificates) {
            this.studentId = studentId;
            this.batches = batches;
            this.certificates = certificates;
        }

        /** Certified courses first: a certificate is the stronger claim. */
        List<Candidate> candidates() {
            List<Candidate> list = new ArrayList<>();
            if (certificates != null) {
                certificates.stream()
                        .filter(c -> "ISSUED".equals(c.status()))
                        .forEach(c -> list.add(new Candidate(c.courseId(), c.batchId(), true)));
            }
            if (batches != null) {
                batches.forEach(b -> list.add(new Candidate(b.courseId(), b.id(), false)));
            }
            return list;
        }

        BatchClient.Attendance attendance(Long batchId, BatchClient client) {
            return attendanceByBatch.computeIfAbsent(batchId, id -> client.attendance(studentId, id));
        }

        AssessmentClient.Completion results(Long courseId, Long batchId, AssessmentClient client) {
            return resultsByBatch.computeIfAbsent(batchId, id -> client.completion(studentId, courseId, id));
        }
    }
}
