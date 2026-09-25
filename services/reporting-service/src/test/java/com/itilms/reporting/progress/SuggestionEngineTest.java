package com.itilms.reporting.progress;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.itilms.reporting.client.AssessmentClient;
import com.itilms.reporting.client.CourseClient;
import com.itilms.reporting.client.LiveClient;
import com.itilms.reporting.progress.StudentProgressReport.Suggestion;

/** Every suggestion has to come from a fact in the numbers, and a healthy student has to be told so. */
class SuggestionEngineTest {

    private static final int THRESHOLD = 75;

    private static SuggestionEngine.Facts facts(List<CourseClient.CourseProgress> courses, Integer attendance, long counted,
                                                LiveClient.Participation live, AssessmentClient.Tests tests,
                                                AssessmentClient.Coding coding, AssessmentClient.Assignments work) {
        return new SuggestionEngine.Facts(courses, attendance, counted, THRESHOLD, live, tests, coding, work);
    }

    private static AssessmentClient.Tests tests(List<AssessmentClient.Pending> pending, List<AssessmentClient.Weak> below,
                                                int terminated) {
        return new AssessmentClient.Tests(3, 2, 70, 90, terminated, 0, pending, below);
    }

    private static AssessmentClient.Assignments work(List<AssessmentClient.Pending> pending, int returned) {
        return new AssessmentClient.Assignments(4, 2, 2, 80, returned, 0, pending);
    }

    private static CourseClient.CourseProgress course(int percent, String status, List<CourseClient.ModuleProgress> modules) {
        return new CourseClient.CourseProgress(3L, "Java Full Stack", 7L, status, percent, 4, 10, 4, 3, modules);
    }

    private static List<String> types(List<Suggestion> suggestions) {
        return suggestions.stream().map(Suggestion::type).toList();
    }

    @Test
    @DisplayName("A student with nothing wrong is told they are on track, not left with an empty box")
    void onTrack() {
        var result = SuggestionEngine.suggest(facts(List.of(course(80, "ACTIVE", List.of(new CourseClient.ModuleProgress(1L, "M", 3, 3)))),
                90, 10, new LiveClient.Participation(5, 5, 300, 95, Instant.now()), tests(List.of(), List.of(), 0),
                new AssessmentClient.Coding(2, 8, 8, 100, List.of()), work(List.of(), 0)));

        assertThat(types(result)).containsExactly("ON_TRACK");
    }

    @Test
    @DisplayName("Missing data (a service was down, or nothing has happened yet) produces no advice at all, only 'on track'")
    void noDataNoAdvice() {
        assertThat(types(SuggestionEngine.suggest(facts(null, null, 0, null, null, null, null)))).containsExactly("ON_TRACK");
    }

    @Test
    @DisplayName("Each problem in the data becomes a suggestion that names it")
    void eachProblemBecomesASuggestion() {
        var result = SuggestionEngine.suggest(facts(
                List.of(course(30, "ACTIVE", List.of(new CourseClient.ModuleProgress(1L, "Basics", 3, 3),
                        new CourseClient.ModuleProgress(2L, "Collections", 4, 1)))),
                60, 10, new LiveClient.Participation(10, 3, 100, 40, Instant.now()),
                tests(List.of(new AssessmentClient.Pending(9L, "Unit test 2", null, false)),
                        List.of(new AssessmentClient.Weak(8L, "Unit test 1", 35, 50)), 1),
                new AssessmentClient.Coding(2, 3, 8, 40, List.of(new AssessmentClient.CodingWeak(5L, "Reverse a string", 1, 4))),
                work(List.of(new AssessmentClient.Pending(4L, "Report", Instant.parse("2026-09-20T00:00:00Z"), true)), 1)));

        // Nine things are wrong; the list keeps the eight most urgent, and the one dropped is the least urgent.
        assertThat(result).hasSize(SuggestionEngine.MAX_SUGGESTIONS);
        assertThat(types(result)).contains("ATTEMPT_TEST", "REVISE_TOPIC", "PRACTICE_CODING", "COMPLETE_MODULE",
                "IMPROVE_ATTENDANCE", "JOIN_LIVE_CLASSES", "SUBMIT_ASSIGNMENT", "REDO_ASSIGNMENT")
                .doesNotContain("STAY_IN_TEST");

        assertThat(result).filteredOn(s -> s.type().equals("COMPLETE_MODULE")).singleElement().satisfies(s -> {
            assertThat(s.title()).contains("Collections");                      // the first unfinished module, not the finished one
            assertThat(s.detail()).contains("1 of 4").contains("Java Full Stack");
            assertThat(s.link()).isEqualTo("/app/courses/3");
        });
        assertThat(result).filteredOn(s -> s.type().equals("REVISE_TOPIC")).singleElement()
                .satisfies(s -> assertThat(s.detail()).contains("35%").contains("50%"));
        assertThat(result).filteredOn(s -> s.type().equals("PRACTICE_CODING")).singleElement()
                .satisfies(s -> assertThat(s.detail()).contains("Reverse a string").contains("1 of 4"));
        assertThat(result).filteredOn(s -> s.type().equals("SUBMIT_ASSIGNMENT")).singleElement()
                .satisfies(s -> assertThat(s.title()).startsWith("Hand in the overdue assignment"));
        assertThat(result).filteredOn(s -> s.type().equals("IMPROVE_ATTENDANCE")).singleElement()
                .satisfies(s -> assertThat(s.detail()).contains("60%").contains("75%"));
    }

    @Test
    @DisplayName("Overdue work and pending tests come before study advice; the list is capped")
    void orderAndCap() {
        var pending = java.util.stream.IntStream.range(0, 6)
                .mapToObj(i -> new AssessmentClient.Pending((long) i, "Test " + i, null, false)).toList();
        var result = SuggestionEngine.suggest(facts(
                List.of(course(10, "ACTIVE", List.of(new CourseClient.ModuleProgress(1L, "Basics", 3, 0)))),
                50, 10, null, tests(pending, List.of(), 0), null,
                work(List.of(new AssessmentClient.Pending(4L, "Report", null, true)), 0)));

        assertThat(result).hasSize(SuggestionEngine.MAX_SUGGESTIONS);
        assertThat(result.get(0).priority()).isEqualTo(1);
        assertThat(result).isSortedAccordingTo(java.util.Comparator.comparingInt(Suggestion::priority));
        assertThat(result.get(0).type()).isIn("SUBMIT_ASSIGNMENT", "ATTEMPT_TEST");
    }

    @Test
    @DisplayName("Attendance and live participation need enough classes before they are judged")
    void tooFewSessions() {
        var result = SuggestionEngine.suggest(facts(List.of(), 0, 2, new LiveClient.Participation(2, 0, 0, null, null),
                null, null, null));

        assertThat(types(result)).containsExactly("ON_TRACK");
    }

    @Test
    @DisplayName("Coding suggestions appear only for a student who has attempted coding and is weak at it")
    void codingOnlyWhenWeak() {
        var strong = SuggestionEngine.suggest(facts(List.of(), null, 0, null, null,
                new AssessmentClient.Coding(3, 9, 10, 90, List.of()), null));
        var none = SuggestionEngine.suggest(facts(List.of(), null, 0, null, null,
                new AssessmentClient.Coding(0, 0, 0, null, List.of()), null));
        var weakNoDetail = SuggestionEngine.suggest(facts(List.of(), null, 0, null, null,
                new AssessmentClient.Coding(3, 3, 10, 30, List.of()), null));

        assertThat(types(strong)).containsExactly("ON_TRACK");
        assertThat(types(none)).containsExactly("ON_TRACK");
        assertThat(types(weakNoDetail)).containsExactly("PRACTICE_CODING");
    }

    @Test
    @DisplayName("A dropped or completed course is not asked to finish its modules")
    void onlyActiveCoursesAskForModules() {
        var open = List.of(new CourseClient.ModuleProgress(1L, "M", 3, 0));

        assertThat(types(SuggestionEngine.suggest(facts(List.of(course(20, "DROPPED", open)), null, 0, null, null, null, null))))
                .containsExactly("ON_TRACK");
        assertThat(types(SuggestionEngine.suggest(facts(List.of(course(100, "COMPLETED", open)), null, 0, null, null, null, null))))
                .containsExactly("ON_TRACK");
    }

    @Test
    @DisplayName("Recorded lessons are suggested only when fewer than half are finished")
    void recordings() {
        var behind = new CourseClient.CourseProgress(3L, "Java", 7L, "ACTIVE", 20, 2, 10, 10, 2, List.of());
        var fine = new CourseClient.CourseProgress(3L, "Java", 7L, "ACTIVE", 60, 6, 10, 10, 6, List.of());

        assertThat(types(SuggestionEngine.suggest(facts(List.of(behind), null, 0, null, null, null, null))))
                .containsExactly("WATCH_RECORDINGS");
        assertThat(types(SuggestionEngine.suggest(facts(List.of(fine), null, 0, null, null, null, null))))
                .containsExactly("ON_TRACK");
    }
}
