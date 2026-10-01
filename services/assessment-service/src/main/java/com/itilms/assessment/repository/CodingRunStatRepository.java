package com.itilms.assessment.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.itilms.assessment.entity.CodingRunStat;

public interface CodingRunStatRepository extends JpaRepository<CodingRunStat, Long> {

    /** Everyone's best figures for one question, capped so a very popular question cannot make this heavy. */
    List<CodingRunStat> findTop5000ByQuestionId(Long questionId);

    /**
     * Keeps the best figures per student. Written as one statement so two runs at once cannot collide on the unique
     * key (an exception from a failed insert would poison the transaction).
     */
    @Modifying
    @Query(value = "INSERT INTO coding_run_stats (question_id, student_id, runtime_ms, memory_kb, updated_at) "
            + "VALUES (:question, :student, :runtime, :memory, NOW()) "
            + "ON CONFLICT (question_id, student_id) DO UPDATE SET "
            + "runtime_ms = LEAST(coding_run_stats.runtime_ms, EXCLUDED.runtime_ms), "
            + "memory_kb = CASE WHEN coding_run_stats.memory_kb IS NULL THEN EXCLUDED.memory_kb "
            + "WHEN EXCLUDED.memory_kb IS NULL THEN coding_run_stats.memory_kb "
            + "ELSE LEAST(coding_run_stats.memory_kb, EXCLUDED.memory_kb) END, "
            + "updated_at = NOW()", nativeQuery = true)
    void keepBest(@Param("question") Long question, @Param("student") Long student,
                  @Param("runtime") int runtime, @Param("memory") Integer memory);
}
