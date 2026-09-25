package com.itilms.reporting.progress;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.itilms.reporting.client.AssessmentClient;
import com.itilms.reporting.client.CourseClient;
import com.itilms.reporting.client.LiveClient;
import com.itilms.reporting.progress.StudentProgressReport.Suggestion;

/**
 * Turns a student's numbers into things to do. Pure: the same numbers always give the same suggestions, and every
 * suggestion names the fact it comes from, so a student can see why they are being told.
 *
 * <p>Nothing here guesses. A suggestion appears only when the data shows the problem: an unfinished module, a test
 * not yet taken, a score below the pass mark, a run of test cases that did not pass, work overdue, attendance
 * below the institute's threshold. A section with no data (null, or nothing attempted yet) says nothing rather
 * than inventing advice.
 */
public final class SuggestionEngine {

    /** Below this share of coding test cases passed, more practice is suggested. */
    static final int CODING_WEAK_PERCENT = 60;
    /** Fewer sessions than this is too little to judge attendance or live participation on. */
    static final int MIN_SESSIONS = 3;
    /** Joining fewer than this share of live classes is worth a nudge. */
    static final int LIVE_WEAK_PERCENT = 60;
    /** Watching fewer than this share of recorded lessons is worth a nudge. */
    static final int RECORDED_WEAK_PERCENT = 50;
    static final int MAX_SUGGESTIONS = 8;

    private SuggestionEngine() {
    }

    /** Everything the rules look at. Any section may be null: it could not be loaded, or has no data yet. */
    public record Facts(List<CourseClient.CourseProgress> courses,
                        Integer attendancePercent, long attendanceCounted, int attendanceThreshold,
                        LiveClient.Participation live,
                        AssessmentClient.Tests tests, AssessmentClient.Coding coding,
                        AssessmentClient.Assignments assignments) {
    }

    public static List<Suggestion> suggest(Facts facts) {
        List<Suggestion> found = new ArrayList<>();

        // Assessed work that is due or overdue comes first: it has a deadline and a cost.
        if (facts.assignments() != null) {
            for (var pending : facts.assignments().pending()) {
                found.add(new Suggestion("SUBMIT_ASSIGNMENT", pending.overdue() ? 1 : 2,
                        (pending.overdue() ? "Hand in the overdue assignment: " : "Submit the assignment: ") + pending.title(),
                        pending.overdue() ? "The deadline has passed; hand it in now, late work may still be marked."
                                : "Not handed in yet.",
                        "/app/assessments/assignments/" + pending.id()));
            }
            if (facts.assignments().returned() > 0) {
                found.add(new Suggestion("REDO_ASSIGNMENT", 1,
                        "Redo the assignment your trainer sent back",
                        facts.assignments().returned() + " assignment(s) need rework before they can be marked.",
                        "/app/assessments"));
            }
        }

        if (facts.tests() != null) {
            for (var pending : facts.tests().pending()) {
                found.add(new Suggestion("ATTEMPT_TEST", 1, "Attempt the pending test: " + pending.title(),
                        pending.dueAt() == null ? "It is open now." : "It is open now and closes " + pending.dueAt() + ".",
                        "/app/assessments/tests/" + pending.id()));
            }
            for (var weak : facts.tests().below()) {
                found.add(new Suggestion("REVISE_TOPIC", 2, "Revise before retaking: " + weak.title(),
                        "You scored " + weak.percent() + "%; the pass mark is " + weak.passPercent() + "%.",
                        "/app/assessments/tests/" + weak.id()));
            }
        }

        if (facts.coding() != null && facts.coding().questionsAttempted() > 0
                && facts.coding().averagePercent() != null && facts.coding().averagePercent() < CODING_WEAK_PERCENT) {
            var lowest = facts.coding().lowest();
            found.add(new Suggestion("PRACTICE_CODING", 2, "Practice coding",
                    lowest.isEmpty()
                            ? "Your programs pass " + facts.coding().averagePercent() + "% of the test cases."
                            : "Start with: " + lowest.get(0).question() + " (" + lowest.get(0).passed() + " of "
                                    + lowest.get(0).total() + " test cases passed).",
                    "/app/assessments"));
        }

        if (facts.courses() != null) {
            for (var course : facts.courses()) {
                if (!"ACTIVE".equals(course.status()) || course.modules() == null) {
                    continue;
                }
                course.modules().stream().filter(m -> m.lessons() > 0 && !m.isComplete()).findFirst().ifPresent(module ->
                        found.add(new Suggestion("COMPLETE_MODULE", course.progressPercent() < 50 ? 2 : 3,
                                "Complete the pending module: " + module.title(),
                                module.completed() + " of " + module.lessons() + " lessons done in "
                                        + (course.courseTitle() == null ? "this course" : course.courseTitle()) + ".",
                                "/app/courses/" + course.courseId())));
            }
            var current = facts.courses().stream().filter(c -> "ACTIVE".equals(c.status())).toList();
            int videos = current.stream().mapToInt(CourseClient.CourseProgress::videoLessons).sum();
            int watched = current.stream().mapToInt(CourseClient.CourseProgress::videoLessonsCompleted).sum();
            if (videos > 0 && percent(watched, videos) < RECORDED_WEAK_PERCENT) {
                found.add(new Suggestion("WATCH_RECORDINGS", 3, "Watch the recorded lessons",
                        "You have finished " + watched + " of " + videos + " recorded lessons.", "/app/courses"));
            }
        }

        if (facts.attendancePercent() != null && facts.attendanceCounted() >= MIN_SESSIONS
                && facts.attendancePercent() < facts.attendanceThreshold()) {
            found.add(new Suggestion("IMPROVE_ATTENDANCE", 2, "Improve your attendance",
                    "You have attended " + facts.attendancePercent() + "% of classes; the institute expects at least "
                            + facts.attendanceThreshold() + "%.", "/app/live-classes"));
        }

        if (facts.live() != null && facts.live().sessionsHeld() >= MIN_SESSIONS
                && percent(facts.live().sessionsJoined(), facts.live().sessionsHeld()) < LIVE_WEAK_PERCENT) {
            found.add(new Suggestion("JOIN_LIVE_CLASSES", 3, "Join more live classes",
                    "You joined " + facts.live().sessionsJoined() + " of " + facts.live().sessionsHeld() + " live classes.",
                    "/app/live-classes"));
        }

        if (facts.tests() != null && facts.tests().terminated() > 0) {
            found.add(new Suggestion("STAY_IN_TEST", 3, "Stay in the test window during secure tests",
                    facts.tests().terminated() + " attempt(s) were ended for leaving the test window.",
                    "/app/assessments"));
        }

        found.sort(Comparator.comparingInt(Suggestion::priority));
        if (found.isEmpty()) {
            return List.of(new Suggestion("ON_TRACK", 3, "You are on track",
                    "Nothing is pending and nothing is falling behind. Keep going.", "/app"));
        }
        return found.size() > MAX_SUGGESTIONS ? List.copyOf(found.subList(0, MAX_SUGGESTIONS)) : List.copyOf(found);
    }

    /** {@code part} as a whole-number percentage of {@code whole}; 0 when there is nothing to divide by. */
    static int percent(long part, long whole) {
        return whole <= 0 ? 0 : (int) Math.round(part * 100.0 / whole);
    }
}
