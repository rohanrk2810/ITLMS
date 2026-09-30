package com.itilms.certificate.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.itilms.certificate.client.AssessmentClient;
import com.itilms.certificate.client.BatchClient;
import com.itilms.certificate.client.CourseClient;
import com.itilms.certificate.client.FinanceClient;
import com.itilms.certificate.config.CertificateProperties;
import com.itilms.certificate.dto.response.EligibilityResponse.Criterion;
import com.itilms.certificate.dto.response.EligibilityResponse.Outcome;

/**
 * The completion rule of Doc S7.3, applied to facts gathered from the services
 * that own them.
 *
 * <p>Every criterion comes out as one of four outcomes, and only MET counts
 * toward eligibility. The one that matters most is UNAVAILABLE: when a service
 * could not be asked, the answer is "not confirmed", never "assume yes". A
 * certificate issued because attendance-service happened to be restarting is a
 * certificate the institute cannot defend.
 *
 * <p>Pure, so each rule can be tested with plain values.
 */
public final class EligibilityRules {

    private EligibilityRules() {
    }

    /**
     * Facts about one student and course. A null field means the owning service
     * could not be asked; an empty fee-plan list means nothing is owed.
     */
    public record Facts(
            CourseClient.Progress progress,
            AssessmentClient.Completion completion,
            BatchClient.AttendanceSummary attendance,
            List<FinanceClient.FeePlan> feePlans,
            Long courseId
    ) {
    }

    public static List<Criterion> evaluate(Facts facts, CertificateProperties.Criteria rules, String currencySymbol) {
        List<Criterion> result = new ArrayList<>();
        result.add(lessons(facts, rules));
        result.add(tests(facts, rules));
        result.add(averageScore(facts, rules));
        result.add(assignments(facts, rules));
        result.add(attendance(facts, rules));
        result.add(fees(facts, rules, currencySymbol));
        return result;
    }

    public static boolean eligible(List<Criterion> criteria) {
        return criteria.stream().allMatch(c -> c.outcome() == Outcome.MET || c.outcome() == Outcome.NOT_REQUIRED);
    }

    // -----------------------------------------------------------------

    private static Criterion lessons(Facts f, CertificateProperties.Criteria rules) {
        String name = "All mandatory lessons complete";
        if (!rules.isRequireAllMandatoryLessons()) {
            return Criterion.notRequired("LESSONS", name);
        }
        if (f.progress() == null) {
            return Criterion.unavailable("LESSONS", name, "Lesson progress could not be checked");
        }
        String detail = "%d of %d mandatory lessons".formatted(f.progress().completedLessons(), f.progress().totalLessons());
        return f.progress().allLessonsComplete()
                ? Criterion.met("LESSONS", name, detail)
                : Criterion.notMet("LESSONS", name, detail);
    }

    private static Criterion tests(Facts f, CertificateProperties.Criteria rules) {
        String name = "All mandatory tests passed";
        if (!rules.isRequireAllQuizzesPassed()) {
            return Criterion.notRequired("TESTS", name);
        }
        if (f.completion() == null) {
            return Criterion.unavailable("TESTS", name, "Test results could not be checked");
        }
        int required = f.completion().testsRequired();
        if (required == 0) {
            return Criterion.met("TESTS", name, "No mandatory tests");
        }
        String detail = "%d of %d passed".formatted(f.completion().testsPassed(), required);
        return f.completion().testsPassed() >= required
                ? Criterion.met("TESTS", name, detail)
                : Criterion.notMet("TESTS", name, detail);
    }

    private static Criterion averageScore(Facts f, CertificateProperties.Criteria rules) {
        int minimum = rules.getMinimumAverageScorePercent();
        String name = "Average test score at least %d%%".formatted(minimum);
        if (minimum <= 0) {
            return Criterion.notRequired("AVERAGE_SCORE", name);
        }
        if (f.completion() == null) {
            return Criterion.unavailable("AVERAGE_SCORE", name, "Test results could not be checked");
        }
        if (f.completion().testsRequired() == 0) {
            return Criterion.met("AVERAGE_SCORE", name, "No mandatory tests");
        }
        Integer average = f.completion().averageTestPercent();
        if (average == null) {
            // Some results are held back by the trainer: not failed, not passed.
            return Criterion.unavailable("AVERAGE_SCORE", name, "Some results are not released yet");
        }
        String detail = "Average %d%%".formatted(average);
        return average >= minimum ? Criterion.met("AVERAGE_SCORE", name, detail)
                : Criterion.notMet("AVERAGE_SCORE", name, detail);
    }

    private static Criterion assignments(Facts f, CertificateProperties.Criteria rules) {
        String name = "All mandatory assignments marked";
        if (!rules.isRequireAllAssignmentsComplete()) {
            return Criterion.notRequired("ASSIGNMENTS", name);
        }
        if (f.completion() == null) {
            return Criterion.unavailable("ASSIGNMENTS", name, "Assignments could not be checked");
        }
        int required = f.completion().assignmentsRequired();
        if (required == 0) {
            return Criterion.met("ASSIGNMENTS", name, "No mandatory assignments");
        }
        String detail = "%d of %d marked".formatted(f.completion().assignmentsEvaluated(), required);
        return f.completion().assignmentsEvaluated() >= required
                ? Criterion.met("ASSIGNMENTS", name, detail)
                : Criterion.notMet("ASSIGNMENTS", name, detail);
    }

    private static Criterion attendance(Facts f, CertificateProperties.Criteria rules) {
        int minimum = rules.getMinimumAttendancePercent();
        String name = "Attendance at least %d%%".formatted(minimum);
        if (!rules.isRequireAttendance()) {
            return Criterion.notRequired("ATTENDANCE", name);
        }
        if (f.attendance() == null) {
            return Criterion.unavailable("ATTENDANCE", name, "Attendance could not be checked");
        }
        if (f.attendance().totalSessions() == 0) {
            // No class was ever registered for this batch: there is no absence
            // to hold against anyone.
            return Criterion.met("ATTENDANCE", name, "No sessions recorded");
        }
        BigDecimal percent = f.attendance().attendancePercent() == null
                ? BigDecimal.ZERO : f.attendance().attendancePercent();
        String detail = "%s%% (%d of %d sessions)".formatted(percent.stripTrailingZeros().toPlainString(),
                f.attendance().attendedSessions(), f.attendance().totalSessions());
        return percent.compareTo(BigDecimal.valueOf(minimum)) >= 0
                ? Criterion.met("ATTENDANCE", name, detail)
                : Criterion.notMet("ATTENDANCE", name, detail);
    }

    private static Criterion fees(Facts f, CertificateProperties.Criteria rules, String symbol) {
        String name = "Fees paid";
        if (!rules.isRequireFeesCleared()) {
            return Criterion.notRequired("FEES", name);
        }
        if (f.feePlans() == null) {
            return Criterion.unavailable("FEES", name, "Fees could not be checked");
        }
        BigDecimal owed = f.feePlans().stream()
                .filter(p -> f.courseId().equals(p.courseId()) && !"CANCELLED".equals(p.status()))
                .map(p -> p.outstanding() == null ? BigDecimal.ZERO : p.outstanding())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return owed.signum() == 0
                ? Criterion.met("FEES", name, "Nothing outstanding")
                : Criterion.notMet("FEES", name, "%s %s outstanding".formatted(symbol, owed.toPlainString()));
    }

    /** The unmet criteria as one sentence, for a refusal message. */
    public static String unmetSummary(List<Criterion> criteria) {
        return criteria.stream()
                .filter(c -> c.outcome() == Outcome.NOT_MET
                        || c.outcome() == Outcome.UNAVAILABLE)
                .map(c -> c.name() + (c.detail() == null ? "" : " (" + c.detail() + ")"))
                .reduce((a, b) -> a + "; " + b).orElse("");
    }
}
