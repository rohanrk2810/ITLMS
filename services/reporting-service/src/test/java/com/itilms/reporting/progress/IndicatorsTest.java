package com.itilms.reporting.progress;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.itilms.reporting.client.AssessmentClient;
import com.itilms.reporting.client.CourseClient;
import com.itilms.reporting.client.LiveClient;
import com.itilms.reporting.progress.StudentProgressReport.Attendance;
import com.itilms.reporting.progress.StudentProgressReport.Indicator;

/** The headline percentages: the arithmetic, and that "no data" is never shown as a failing 0%. */
class IndicatorsTest {

    private static Indicator find(List<Indicator> all, String key) {
        return all.stream().filter(i -> i.key().equals(key)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("Each headline number is the honest arithmetic of its section, with a band")
    void numbers() {
        var courses = List.of(
                new CourseClient.CourseProgress(3L, "Java", 7L, "ACTIVE", 75, 6, 8, 10, 8, List.of()),
                new CourseClient.CourseProgress(4L, "Web", 8L, "COMPLETED", 100, 4, 4, 0, 0, List.of()),
                new CourseClient.CourseProgress(5L, "Old", 9L, "DROPPED", 10, 1, 10, 5, 0, List.of()));
        var attendance = new Attendance(88, 22, 25, 3, 1, List.of());
        var live = new LiveClient.Participation(10, 8, 480, 80, null);
        var tests = new AssessmentClient.Tests(4, 3, 82, 95, 0, 0, List.of(), List.of());
        var coding = new AssessmentClient.Coding(3, 9, 12, 76, List.of());
        var work = new AssessmentClient.Assignments(5, 4, 4, 90, 0, 0, List.of());

        var all = Indicators.build(courses, attendance, 75, live, tests, coding, work);

        assertThat(all).extracting(Indicator::key).containsExactly("COURSE_PROGRESS", "ATTENDANCE", "LIVE_CLASSES",
                "RECORDED_LESSONS", "TESTS", "CODING", "ASSIGNMENTS");
        // A dropped course does not drag the average down: (75 + 100) / 2, rounded.
        assertThat(find(all, "COURSE_PROGRESS").percent()).isEqualTo(88);
        assertThat(find(all, "COURSE_PROGRESS").detail()).contains("10 of 12 lessons").contains("2 course(s)");
        assertThat(find(all, "ATTENDANCE").percent()).isEqualTo(88);
        assertThat(find(all, "ATTENDANCE").band()).isEqualTo("GOOD");
        assertThat(find(all, "LIVE_CLASSES").percent()).isEqualTo(80);
        // 8 of the 10 videos of the courses still taken; the dropped course's 5 videos are not counted.
        assertThat(find(all, "RECORDED_LESSONS").percent()).isEqualTo(80);
        assertThat(find(all, "TESTS").percent()).isEqualTo(82);
        assertThat(find(all, "TESTS").detail()).isEqualTo("Passed 3 of 4 tests");
        assertThat(find(all, "CODING").percent()).isEqualTo(76);
        assertThat(find(all, "CODING").detail()).isEqualTo("9 of 12 test cases passed across 3 question(s)");
        assertThat(find(all, "ASSIGNMENTS").percent()).isEqualTo(90);
        assertThat(find(all, "ASSIGNMENTS").band()).isEqualTo("GOOD");
    }

    @Test
    @DisplayName("Nothing behind a number means NONE with an explanation, never 0%")
    void noDataIsNotZero() {
        var all = Indicators.build(null, null, 75, null, null, null, null);

        assertThat(all).allSatisfy(i -> {
            assertThat(i.percent()).isNull();
            assertThat(i.band()).isEqualTo("NONE");
            assertThat(i.detail()).isNotBlank();
        });

        var empty = Indicators.build(List.of(), new Attendance(null, 0, 0, 0, 0, List.of()), 75,
                new LiveClient.Participation(0, 0, 0, null, null),
                new AssessmentClient.Tests(0, 0, null, null, 0, 0, List.of(), List.of()),
                new AssessmentClient.Coding(0, 0, 0, null, List.of()),
                new AssessmentClient.Assignments(0, 0, 0, null, 0, 0, List.of()));
        assertThat(empty).allSatisfy(i -> assertThat(i.band()).isEqualTo("NONE"));
    }

    @Test
    @DisplayName("Bands: 75 and over good, 50 to 74 fair, below 50 low")
    void bands() {
        assertThat(Indicators.band(75)).isEqualTo("GOOD");
        assertThat(Indicators.band(74)).isEqualTo("FAIR");
        assertThat(Indicators.band(50)).isEqualTo("FAIR");
        assertThat(Indicators.band(49)).isEqualTo("LOW");
        assertThat(Indicators.band(0)).isEqualTo("LOW");
        assertThat(Indicators.band(null)).isEqualTo("NONE");
    }

    @Test
    @DisplayName("Attendance is judged against the institute's own line, not the generic bands")
    void attendanceUsesTheThreshold() {
        assertThat(find(Indicators.build(null, new Attendance(70, 7, 10, 3, 0, List.of()), 75, null, null, null, null), "ATTENDANCE").band())
                .isEqualTo("LOW");
        assertThat(find(Indicators.build(null, new Attendance(80, 8, 10, 2, 0, List.of()), 75, null, null, null, null), "ATTENDANCE").band())
                .isEqualTo("FAIR");
        assertThat(find(Indicators.build(null, new Attendance(90, 9, 10, 1, 0, List.of()), 75, null, null, null, null), "ATTENDANCE").band())
                .isEqualTo("GOOD");
        // A stricter institute moves the line: 80% is low where the threshold is 85.
        assertThat(find(Indicators.build(null, new Attendance(80, 8, 10, 2, 0, List.of()), 85, null, null, null, null), "ATTENDANCE").band())
                .isEqualTo("LOW");
    }

    @Test
    @DisplayName("Results a trainer has not released are mentioned, not silently dropped")
    void heldBackResultsMentioned() {
        var tests = new AssessmentClient.Tests(3, 1, 90, 90, 0, 2, List.of(), List.of());

        assertThat(find(Indicators.build(null, null, 75, null, tests, null, null), "TESTS").detail())
                .contains("2 result(s) not released yet");
    }

    @Test
    @DisplayName("Assignments handed in but not yet marked show progress without a mark")
    void assignmentsNotMarkedYet() {
        var work = new AssessmentClient.Assignments(3, 2, 0, null, 0, 0, List.of());

        var indicator = find(Indicators.build(null, null, 75, null, null, null, work), "ASSIGNMENTS");

        assertThat(indicator.percent()).isNull();
        assertThat(indicator.detail()).isEqualTo("0 of 3 assignments marked so far");
    }
}
