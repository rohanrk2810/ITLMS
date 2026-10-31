package com.itilms.assessment.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Attempts a trainer gave one student beyond what the test allows everyone (V18): a retake after a bad connection, or after a
 * secure test was ended. Kept per test and student; the student's allowance is the test's plus this.
 */
@Component
public class ExtraAttemptStore {

    /** What one student may be given on one test, however many times the trainer presses the button. */
    public static final int MAX_EXTRA = 20;

    @PersistenceContext
    private EntityManager em;

    @Transactional(readOnly = true)
    public int extraFor(Long quizId, Long studentId) {
        List<?> rows = em.createNativeQuery("SELECT extra FROM quiz_extra_attempts WHERE quiz_id = :q AND student_id = :s")
                .setParameter("q", quizId).setParameter("s", studentId).getResultList();
        return rows.isEmpty() ? 0 : ((Number) rows.get(0)).intValue();
    }

    /** This student's extra attempts on each of these tests; tests with none are absent. */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public Map<Long, Integer> extraForStudent(Long studentId, Collection<Long> quizIds) {
        Map<Long, Integer> out = new HashMap<>();
        if (quizIds.isEmpty()) {
            return out;
        }
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT quiz_id, extra FROM quiz_extra_attempts WHERE student_id = :s AND quiz_id IN (:ids)")
                .setParameter("s", studentId).setParameter("ids", quizIds).getResultList();
        for (Object[] row : rows) {
            out.put(((Number) row[0]).longValue(), ((Number) row[1]).intValue());
        }
        return out;
    }

    /** Adds to the student's extra attempts (never above {@link #MAX_EXTRA}) and returns the new total. */
    @Transactional
    public int grant(Long quizId, Long studentId, int count, Long grantedBy) {
        Object total = em.createNativeQuery("""
                INSERT INTO quiz_extra_attempts (quiz_id, student_id, extra, granted_by, updated_at)
                VALUES (:q, :s, LEAST(:n, :max), :by, now())
                ON CONFLICT (quiz_id, student_id) DO UPDATE SET
                    extra = LEAST(quiz_extra_attempts.extra + :n, :max),
                    granted_by = :by,
                    updated_at = now()
                RETURNING extra
                """)
                .setParameter("q", quizId).setParameter("s", studentId).setParameter("n", count)
                .setParameter("max", MAX_EXTRA).setParameter("by", grantedBy)
                .getSingleResult();
        return ((Number) total).intValue();
    }
}
