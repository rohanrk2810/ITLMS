package com.itilms.assessment.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * How much of each coding answer was typed and how much pasted, kept up to date as the edits arrive (V16), so the
 * trainer's results sheet can flag a pasted answer without reading every replay.
 */
@Component
public class TypingStatsStore {

    /** Characters inserted by typing and by pasting in a batch of edits, and the biggest single paste. */
    public record Counts(int typed, int pasted, int largestPaste) {
        public boolean isEmpty() {
            return typed == 0 && pasted == 0;
        }
    }

    /** At least this many pasted characters, and most of the answer, before a result is flagged. */
    static final int NOTABLE_PASTED = 80;
    static final int NOTABLE_SHARE_PERCENT = 60;

    @PersistenceContext
    private EntityManager em;

    private final ObjectMapper json = new ObjectMapper();

    /**
     * Counts one batch of edits: [time_ms, offset, removed_length, inserted_text, pasted(0|1)] each. An edit that
     * replaces the whole text (removed = -1) is the starting snapshot, neither typed nor pasted. Anything unreadable
     * counts as nothing: the stats are a hint for the trainer, never a reason to refuse a batch.
     */
    public Counts count(String events) {
        int typed = 0;
        int pasted = 0;
        int largest = 0;
        try {
            JsonNode list = json.readTree(events);
            if (list != null && list.isArray()) {
                for (JsonNode edit : list) {
                    if (!edit.isArray() || edit.size() < 4 || edit.get(2).asInt() == -1) {
                        continue;
                    }
                    int length = edit.get(3).asText("").length();
                    if (edit.size() > 4 && edit.get(4).asInt() == 1) {
                        pasted += length;
                        largest = Math.max(largest, length);
                    } else {
                        typed += length;
                    }
                }
            }
        } catch (Exception ex) {
            return new Counts(0, 0, 0);
        }
        return new Counts(typed, pasted, largest);
    }

    @Transactional
    public void add(Long attemptId, Long questionId, Counts counts) {
        if (counts.isEmpty()) {
            return;
        }
        em.createNativeQuery("""
                INSERT INTO quiz_typing_stats (attempt_id, question_id, typed_chars, pasted_chars, largest_paste)
                VALUES (:a, :q, :t, :p, :l)
                ON CONFLICT (attempt_id, question_id) DO UPDATE SET
                    typed_chars = quiz_typing_stats.typed_chars + :t,
                    pasted_chars = quiz_typing_stats.pasted_chars + :p,
                    largest_paste = GREATEST(quiz_typing_stats.largest_paste, :l)
                """)
                .setParameter("a", attemptId).setParameter("q", questionId)
                .setParameter("t", counts.typed()).setParameter("p", counts.pasted()).setParameter("l", counts.largestPaste())
                .executeUpdate();
    }

    /** Per attempt, all its coding answers together. Attempts with nothing recorded are absent. */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public Map<Long, Counts> byAttempt(Collection<Long> attemptIds) {
        Map<Long, Counts> out = new HashMap<>();
        if (attemptIds.isEmpty()) {
            return out;
        }
        List<Object[]> rows = em.createNativeQuery("""
                SELECT attempt_id, SUM(typed_chars), SUM(pasted_chars), MAX(largest_paste)
                  FROM quiz_typing_stats WHERE attempt_id IN (:ids) GROUP BY attempt_id
                """).setParameter("ids", attemptIds).getResultList();
        for (Object[] row : rows) {
            out.put(((Number) row[0]).longValue(),
                    new Counts(((Number) row[1]).intValue(), ((Number) row[2]).intValue(), ((Number) row[3]).intValue()));
        }
        return out;
    }

    /** The note for the trainer's sheet, or null when the typing looks ordinary. */
    public static String review(Counts counts) {
        if (counts == null) {
            return null;
        }
        long total = (long) counts.typed() + counts.pasted();
        if (counts.pasted() >= NOTABLE_PASTED && counts.pasted() * 100L >= NOTABLE_SHARE_PERCENT * total) {
            return "Pasted %d of %d characters of code (largest paste %d)"
                    .formatted(counts.pasted(), total, counts.largestPaste());
        }
        return null;
    }
}
