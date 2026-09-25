package com.itilms.assessment.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.itilms.assessment.entity.QuizViolation;
import com.itilms.assessment.entity.ViolationType;

public interface QuizViolationRepository extends JpaRepository<QuizViolation, Long> {

    List<QuizViolation> findByAttemptIdOrderByOccurredAtAsc(Long attemptId);

    long countByAttemptId(Long attemptId);

    /** Events of the given kinds per attempt, in one query, for the trainer's results sheet. */
    @Query("SELECT v.attemptId, COUNT(v) FROM QuizViolation v WHERE v.attemptId IN :attemptIds AND v.type IN :types "
            + "GROUP BY v.attemptId")
    List<Object[]> countByAttemptAndTypes(@Param("attemptIds") Collection<Long> attemptIds,
                                          @Param("types") Collection<ViolationType> types);

    /** The last event that counted, to fold a second report of the same leaving into the first. */
    Optional<QuizViolation> findFirstByAttemptIdAndCountedTrueOrderByOccurredAtDesc(Long attemptId);
}
