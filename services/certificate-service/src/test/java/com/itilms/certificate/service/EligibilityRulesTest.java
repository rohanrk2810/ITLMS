package com.itilms.certificate.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.itilms.certificate.client.AssessmentClient;
import com.itilms.certificate.client.BatchClient;
import com.itilms.certificate.client.CourseClient;
import com.itilms.certificate.client.FinanceClient;
import com.itilms.certificate.config.CertificateProperties;
import com.itilms.certificate.dto.response.EligibilityResponse.Criterion;
import com.itilms.certificate.dto.response.EligibilityResponse.Outcome;

/** The completion rule of Doc S7.3, case by case. */
class EligibilityRulesTest {

    private static final long COURSE = 1L;

    private final CertificateProperties.Criteria rules = new CertificateProperties.Criteria();

    private static CourseClient.Progress lessonsDone(boolean done) {
        return new CourseClient.Progress(3L, COURSE, 9L, BigDecimal.valueOf(done ? 100 : 80), done ? 10 : 8, 10, done);
    }

    private static AssessmentClient.Completion assessed(int testsPassed, Integer average, int marked) {
        return new AssessmentClient.Completion(2, testsPassed, average, 3, marked, false, List.of());
    }

    private static BatchClient.AttendanceSummary attended(int attended, int total) {
        BigDecimal percent = total == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(attended * 100L / total);
        return new BatchClient.AttendanceSummary(3L, 9L, attended, total, percent);
    }

    private static FinanceClient.FeePlan owing(long courseId, long rupees, String status) {
        return new FinanceClient.FeePlan(1L, courseId, BigDecimal.valueOf(30_000), BigDecimal.valueOf(rupees), status);
    }

    private EligibilityRules.Facts complete() {
        return new EligibilityRules.Facts(lessonsDone(true), assessed(2, 72, 3), attended(18, 20),
                List.of(owing(COURSE, 0, "SETTLED")), COURSE);
    }

    private Outcome outcomeOf(List<Criterion> criteria, String key) {
        return criteria.stream().filter(c -> c.key().equals(key)).findFirst().orElseThrow().outcome();
    }

    @Test
    @DisplayName("Everything done: eligible")
    void allMet() {
        assertThat(EligibilityRules.eligible(EligibilityRules.evaluate(complete(), rules, "Rs."))).isTrue();
    }

    @Test
    @DisplayName("A service that could not be asked is never treated as a yes")
    void unavailableIsNotMet() {
        var facts = new EligibilityRules.Facts(lessonsDone(true), assessed(2, 72, 3), null,
                List.of(owing(COURSE, 0, "SETTLED")), COURSE);
        var criteria = EligibilityRules.evaluate(facts, rules, "Rs.");

        assertThat(outcomeOf(criteria, "ATTENDANCE")).isEqualTo(Outcome.UNAVAILABLE);
        assertThat(EligibilityRules.eligible(criteria)).isFalse();
    }

    @Test
    @DisplayName("Attendance below the minimum withholds the certificate")
    void lowAttendance() {
        var facts = new EligibilityRules.Facts(lessonsDone(true), assessed(2, 72, 3), attended(14, 20),
                List.of(), COURSE);
        var criteria = EligibilityRules.evaluate(facts, rules, "Rs.");

        assertThat(outcomeOf(criteria, "ATTENDANCE")).isEqualTo(Outcome.NOT_MET);
        assertThat(EligibilityRules.eligible(criteria)).isFalse();
    }

    @Test
    @DisplayName("Fees owed on this course withhold it; another course's fees and cancelled plans do not")
    void feesForThisCourseOnly() {
        var owesHere = new EligibilityRules.Facts(lessonsDone(true), assessed(2, 72, 3), attended(18, 20),
                List.of(owing(COURSE, 5_000, "ACTIVE")), COURSE);
        var owesElsewhere = new EligibilityRules.Facts(lessonsDone(true), assessed(2, 72, 3), attended(18, 20),
                List.of(owing(2L, 5_000, "ACTIVE"), owing(COURSE, 9_000, "CANCELLED")), COURSE);

        assertThat(outcomeOf(EligibilityRules.evaluate(owesHere, rules, "Rs."), "FEES")).isEqualTo(Outcome.NOT_MET);
        assertThat(outcomeOf(EligibilityRules.evaluate(owesElsewhere, rules, "Rs."), "FEES")).isEqualTo(Outcome.MET);
    }

    @Test
    @DisplayName("A marked-but-not-yet-complete assignment set and a failed test each block it")
    void assessmentGaps() {
        var facts = new EligibilityRules.Facts(lessonsDone(true), assessed(1, 72, 2), attended(18, 20),
                List.of(), COURSE);
        var criteria = EligibilityRules.evaluate(facts, rules, "Rs.");

        assertThat(outcomeOf(criteria, "TESTS")).isEqualTo(Outcome.NOT_MET);
        assertThat(outcomeOf(criteria, "ASSIGNMENTS")).isEqualTo(Outcome.NOT_MET);
    }

    @Test
    @DisplayName("Results the trainer is still holding back leave the average unconfirmed, not failed")
    void heldBackAverage() {
        var facts = new EligibilityRules.Facts(lessonsDone(true), assessed(2, null, 3), attended(18, 20),
                List.of(), COURSE);
        assertThat(outcomeOf(EligibilityRules.evaluate(facts, rules, "Rs."), "AVERAGE_SCORE"))
                .isEqualTo(Outcome.UNAVAILABLE);
    }

    @Test
    @DisplayName("A batch with no sessions on record has no absence to hold against anyone")
    void noSessions() {
        var facts = new EligibilityRules.Facts(lessonsDone(true), assessed(2, 72, 3), attended(0, 0),
                List.of(), COURSE);
        assertThat(outcomeOf(EligibilityRules.evaluate(facts, rules, "Rs."), "ATTENDANCE")).isEqualTo(Outcome.MET);
    }

    @Test
    @DisplayName("A criterion the institute switched off does not block anyone")
    void switchedOff() {
        rules.setRequireAttendance(false);
        var facts = new EligibilityRules.Facts(lessonsDone(true), assessed(2, 72, 3), attended(2, 20),
                List.of(), COURSE);
        var criteria = EligibilityRules.evaluate(facts, rules, "Rs.");

        assertThat(outcomeOf(criteria, "ATTENDANCE")).isEqualTo(Outcome.NOT_REQUIRED);
        assertThat(EligibilityRules.eligible(criteria)).isTrue();
    }
}
