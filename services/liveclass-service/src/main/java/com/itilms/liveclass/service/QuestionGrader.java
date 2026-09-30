package com.itilms.liveclass.service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import com.itilms.liveclass.entity.QuestionType;

/**
 * Marks the answers that can be marked by comparison. Nothing here runs code: a coding answer is kept for the
 * trainer to read, and an open question has no right answer. {@code null} means "a person has to look at this".
 */
public final class QuestionGrader {

    private QuestionGrader() {
    }

    public static Boolean grade(QuestionType type, List<Integer> correct, List<String> accepted,
                                List<Integer> selected, String text) {
        switch (type) {
            case MCQ, TRUE_FALSE:
                return selected != null && selected.size() == 1 && correct != null && correct.size() == 1
                        && selected.get(0).equals(correct.get(0));
            case MULTIPLE_SELECT:
                return selected != null && correct != null && !selected.isEmpty()
                        && new HashSet<>(selected).equals(new HashSet<>(correct));
            case SHORT_ANSWER:
                if (accepted == null || accepted.isEmpty()) {
                    return null;
                }
                String given = normalise(text);
                return !given.isEmpty() && accepted.stream().anyMatch(a -> normalise(a).equals(given));
            default:
                return null;
        }
    }

    static String normalise(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /** Seconds into the class, never negative: a question asked before the scheduled start is at zero. */
    public static int offsetSeconds(Instant classStart, Instant askedAt) {
        long seconds = Duration.between(classStart, askedAt).getSeconds();
        return (int) Math.max(0, seconds);
    }

    /** 2142 becomes 00:35:42. */
    public static String label(int offsetSeconds) {
        return String.format("%02d:%02d:%02d", offsetSeconds / 3600, (offsetSeconds % 3600) / 60, offsetSeconds % 60);
    }
}
