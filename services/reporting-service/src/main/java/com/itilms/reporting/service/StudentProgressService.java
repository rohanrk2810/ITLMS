package com.itilms.reporting.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.ForbiddenOperationException;
import com.itilms.common.security.AppPrincipal;
import com.itilms.common.security.SecurityUtils;
import com.itilms.reporting.client.AdmissionClient;
import com.itilms.reporting.client.AssessmentClient;
import com.itilms.reporting.client.BatchClient;
import com.itilms.reporting.client.CourseClient;
import com.itilms.reporting.client.LiveClient;
import com.itilms.reporting.config.ReportingProperties;
import com.itilms.reporting.progress.Indicators;
import com.itilms.reporting.progress.StudentProgressReport;
import com.itilms.reporting.progress.StudentProgressReport.Attendance;
import com.itilms.reporting.progress.StudentProgressReport.BatchAttendance;
import com.itilms.reporting.progress.SuggestionEngine;
import com.itilms.reporting.repository.StudentSessionAttendanceRepository;

import lombok.RequiredArgsConstructor;

/**
 * A student's complete record and progress report, put together from the services that own each part.
 *
 * <p>Who may see whose report is decided here and nowhere else (the services it calls trust it, and the gateway
 * hides their internal endpoints): a student sees only their own, staff see anyone's, and a trainer sees only
 * students who are enrolled in a batch they teach. A student's own copy holds back test results their trainer
 * has not released, exactly as their results page does.
 */
@Service
@RequiredArgsConstructor
public class StudentProgressService {

    private final BatchClient batches;
    private final CourseClient courses;
    private final LiveClient live;
    private final AssessmentClient assessments;
    private final AdmissionClient admission;
    private final StudentSessionAttendanceRepository attendanceRepository;
    private final ReportingProperties properties;

    /** The signed-in student's own report. */
    public StudentProgressReport mine() {
        AppPrincipal me = SecurityUtils.requirePrincipal();
        if (!me.isStudent() || me.profileId() == null) {
            throw new ForbiddenOperationException("Only a student with a linked profile has a progress report of their own.");
        }
        return build(me.profileId(), true, batches.enrollmentsOf(me.profileId()));
    }

    /** Any student's report, for those allowed to see it. */
    public StudentProgressReport of(Long studentId) {
        AppPrincipal me = SecurityUtils.requirePrincipal();
        List<BatchClient.Enrollment> enrollments = batches.enrollmentsOf(studentId);
        if (me.isStaff()) {
            return build(studentId, false, enrollments);
        }
        if (me.isTrainer()) {
            requireTeaches(enrollments);
            return build(studentId, false, enrollments);
        }
        if (me.isStudent() && studentId.equals(me.profileId())) {
            return build(studentId, true, enrollments);
        }
        throw new ForbiddenOperationException("You may not view this student's progress report.");
    }

    // -----------------------------------------------------------------

    /** A trainer sees a student only through a batch they teach, checked now rather than assumed. */
    private void requireTeaches(List<BatchClient.Enrollment> enrollments) {
        List<BatchClient.MyBatch> mine = batches.myBatches();
        if (mine == null || enrollments == null) {
            throw new BusinessRuleException("Your batches could not be checked just now. Please try again shortly.");
        }
        Set<Long> taught = mine.stream().map(BatchClient.MyBatch::id).collect(Collectors.toSet());
        if (enrollments.stream().noneMatch(e -> taught.contains(e.batchId()))) {
            throw new ForbiddenOperationException("This student is not in a batch you teach.");
        }
    }

    private StudentProgressReport build(Long studentId, boolean forStudent, List<BatchClient.Enrollment> enrollments) {
        List<String> unavailable = new ArrayList<>();
        if (enrollments == null) {
            unavailable.add("enrollments");
        }
        List<BatchClient.Enrollment> all = enrollments == null ? List.of() : enrollments;
        List<BatchClient.Enrollment> active = all.stream().filter(BatchClient.Enrollment::isActive).toList();
        List<Long> batchIds = active.stream().map(BatchClient.Enrollment::batchId).distinct().toList();
        List<Long> courseIds = active.stream().map(BatchClient.Enrollment::courseId).distinct().toList();

        var profile = profile(studentId, unavailable);

        List<CourseClient.CourseProgress> courseProgress = courses.progressOf(studentId);
        if (courseProgress == null) {
            unavailable.add("courses");
        }

        LiveClient.Participation participation = batchIds.isEmpty()
                ? new LiveClient.Participation(0, 0, 0, null, null)
                : live.participation(studentId, batchIds);
        if (participation == null) {
            unavailable.add("liveClasses");
        }

        AssessmentClient.Performance performance = assessments.performance(studentId, batchIds, courseIds, forStudent);
        if (performance == null) {
            unavailable.add("assessments");
        }
        var tests = performance == null ? null : performance.tests();
        var coding = performance == null ? null : performance.coding();
        var assignments = performance == null ? null : performance.assignments();

        Attendance attendance = attendance(studentId, all);
        int threshold = properties.getAttendanceThreshold();

        return new StudentProgressReport(studentId, profile, Instant.now(),
                Indicators.build(courseProgress, attendance, threshold, participation, tests, coding, assignments),
                all, courseProgress, attendance, participation, tests, coding, assignments,
                SuggestionEngine.suggest(new SuggestionEngine.Facts(courseProgress, attendance.percent(),
                        attendance.counted(), threshold, participation, tests, coding, assignments)),
                List.copyOf(unavailable));
    }

    private StudentProgressReport.Profile profile(Long studentId, List<String> unavailable) {
        var found = admission.lookup(List.of(studentId));
        if (found == null) {
            unavailable.add("profile");
            return null;
        }
        return found.stream().filter(s -> studentId.equals(s.id())).findFirst()
                .map(s -> new StudentProgressReport.Profile(s.fullName(), s.studentCode(), s.email(), s.phone(), s.status()))
                .orElse(null);
    }

    /** Class attendance from the register mirror, overall and batch by batch; excused absences are left out. */
    private Attendance attendance(Long studentId, List<BatchClient.Enrollment> enrollments) {
        Map<Long, long[]> byBatch = new HashMap<>();   // present, absent, late, excused
        for (Object[] row : attendanceRepository.countsByBatchAndStatus(studentId)) {
            long[] counts = byBatch.computeIfAbsent((Long) row[0], id -> new long[4]);
            long n = ((Number) row[2]).longValue();
            switch ((String) row[1]) {
                case "PRESENT" -> counts[0] += n;
                case "ABSENT" -> counts[1] += n;
                case "LATE" -> counts[2] += n;
                case "EXCUSED" -> counts[3] += n;
                default -> { }
            }
        }
        Map<Long, String> codes = enrollments.stream().filter(e -> e.batchCode() != null)
                .collect(Collectors.toMap(BatchClient.Enrollment::batchId, BatchClient.Enrollment::batchCode, (a, b) -> a));

        long[] total = new long[4];
        List<BatchAttendance> perBatch = new ArrayList<>();
        byBatch.forEach((batchId, c) -> {
            for (int i = 0; i < 4; i++) {
                total[i] += c[i];
            }
            var pct = new AttendancePercentage(c[0], c[1], c[2], c[3]);
            perBatch.add(new BatchAttendance(batchId, codes.get(batchId), c[0] + c[2], pct.countedSessions(),
                    pct.countedSessions() == 0 ? null : pct.percent().setScale(0, java.math.RoundingMode.HALF_UP).intValue()));
        });
        perBatch.sort(java.util.Comparator.comparing(BatchAttendance::batchId));

        var overall = new AttendancePercentage(total[0], total[1], total[2], total[3]);
        return new Attendance(overall.countedSessions() == 0 ? null : overall.percent().setScale(0, java.math.RoundingMode.HALF_UP).intValue(),
                total[0] + total[2], overall.countedSessions(), total[1], total[3], List.copyOf(perBatch));
    }
}
