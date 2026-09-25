package com.itilms.reporting.progress;

import java.util.ArrayList;
import java.util.List;

import com.itilms.reporting.client.AssessmentClient;
import com.itilms.reporting.client.CourseClient;
import com.itilms.reporting.client.LiveClient;
import com.itilms.reporting.progress.StudentProgressReport.Attendance;
import com.itilms.reporting.progress.StudentProgressReport.Indicator;

/**
 * The headline numbers of the report: course progress, attendance, live classes, recorded lessons, tests, coding
 * and assignments, each as a percentage with a band. A number with nothing behind it is shown as "no data yet"
 * (band NONE), never as 0%: a new student has not failed anything.
 */
public final class Indicators {

    public static final String GOOD = "GOOD";
    public static final String FAIR = "FAIR";
    public static final String LOW = "LOW";
    public static final String NONE = "NONE";

    private Indicators() {
    }

    public static List<Indicator> build(List<CourseClient.CourseProgress> courses, Attendance attendance,
                                        int attendanceThreshold, LiveClient.Participation live,
                                        AssessmentClient.Tests tests, AssessmentClient.Coding coding,
                                        AssessmentClient.Assignments assignments) {
        List<Indicator> indicators = new ArrayList<>();
        indicators.add(courseProgress(courses));
        indicators.add(attendance(attendance, attendanceThreshold));
        indicators.add(live(live));
        indicators.add(recorded(courses));
        indicators.add(tests(tests));
        indicators.add(coding(coding));
        indicators.add(assignments(assignments));
        return indicators;
    }

    static String band(Integer percent) {
        if (percent == null) {
            return NONE;
        }
        return percent >= 75 ? GOOD : percent >= 50 ? FAIR : LOW;
    }

    private static Indicator courseProgress(List<CourseClient.CourseProgress> courses) {
        var taken = courses == null ? List.<CourseClient.CourseProgress>of()
                : courses.stream().filter(c -> !"DROPPED".equals(c.status())).toList();
        if (taken.isEmpty()) {
            return new Indicator("COURSE_PROGRESS", "Course progress", null, NONE, "Not enrolled in a course yet");
        }
        int percent = (int) Math.round(taken.stream().mapToInt(CourseClient.CourseProgress::progressPercent).average().orElse(0));
        int done = taken.stream().mapToInt(CourseClient.CourseProgress::completedLessons).sum();
        int total = taken.stream().mapToInt(CourseClient.CourseProgress::totalLessons).sum();
        return new Indicator("COURSE_PROGRESS", "Course progress", percent, band(percent),
                done + " of " + total + " lessons finished in " + taken.size() + " course(s)");
    }

    private static Indicator attendance(Attendance attendance, int threshold) {
        if (attendance == null || attendance.percent() == null) {
            return new Indicator("ATTENDANCE", "Attendance", null, NONE, "No classes marked yet");
        }
        int percent = attendance.percent();
        // Attendance is judged against the institute's own line, not the generic bands.
        String band = percent < threshold ? LOW : percent < threshold + 10 ? FAIR : GOOD;
        return new Indicator("ATTENDANCE", "Attendance", percent, band,
                "Attended " + attendance.attended() + " of " + attendance.counted() + " classes");
    }

    private static Indicator live(LiveClient.Participation live) {
        if (live == null || live.sessionsHeld() == 0) {
            return new Indicator("LIVE_CLASSES", "Live class participation", null, NONE, "No live classes held yet");
        }
        int percent = SuggestionEngine.percent(live.sessionsJoined(), live.sessionsHeld());
        return new Indicator("LIVE_CLASSES", "Live class participation", percent, band(percent),
                "Joined " + live.sessionsJoined() + " of " + live.sessionsHeld() + " live classes, "
                        + live.minutesInRoom() + " minutes in the room");
    }

    private static Indicator recorded(List<CourseClient.CourseProgress> courses) {
        // A course the student dropped is no longer theirs to watch.
        var taken = courses == null ? List.<CourseClient.CourseProgress>of()
                : courses.stream().filter(c -> !"DROPPED".equals(c.status())).toList();
        int videos = taken.stream().mapToInt(CourseClient.CourseProgress::videoLessons).sum();
        if (videos == 0) {
            return new Indicator("RECORDED_LESSONS", "Recorded lessons", null, NONE, "No recorded lessons in your courses");
        }
        int watched = taken.stream().mapToInt(CourseClient.CourseProgress::videoLessonsCompleted).sum();
        int percent = SuggestionEngine.percent(watched, videos);
        return new Indicator("RECORDED_LESSONS", "Recorded lessons", percent, band(percent),
                "Finished " + watched + " of " + videos + " recorded lessons");
    }

    private static Indicator tests(AssessmentClient.Tests tests) {
        if (tests == null || tests.attempted() == 0) {
            return new Indicator("TESTS", "Test performance", null, NONE, "No tests taken yet");
        }
        String detail = "Passed " + tests.passed() + " of " + tests.attempted() + " tests"
                + (tests.resultsPending() > 0 ? " (" + tests.resultsPending() + " result(s) not released yet)" : "");
        return new Indicator("TESTS", "Test performance", tests.averagePercent(), band(tests.averagePercent()), detail);
    }

    private static Indicator coding(AssessmentClient.Coding coding) {
        if (coding == null || coding.questionsAttempted() == 0) {
            return new Indicator("CODING", "Coding performance", null, NONE, "No coding questions attempted yet");
        }
        return new Indicator("CODING", "Coding performance", coding.averagePercent(), band(coding.averagePercent()),
                coding.testCasesPassed() + " of " + coding.testCasesTotal() + " test cases passed across "
                        + coding.questionsAttempted() + " question(s)");
    }

    private static Indicator assignments(AssessmentClient.Assignments work) {
        if (work == null || work.assigned() == 0) {
            return new Indicator("ASSIGNMENTS", "Assignment performance", null, NONE, "No assignments set yet");
        }
        String detail = work.evaluated() + " of " + work.assigned() + " assignments marked";
        if (work.averagePercent() == null) {
            return new Indicator("ASSIGNMENTS", "Assignment performance", null, NONE, detail + " so far");
        }
        return new Indicator("ASSIGNMENTS", "Assignment performance", work.averagePercent(), band(work.averagePercent()), detail);
    }
}
