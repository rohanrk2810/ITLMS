package com.itilms.assessment.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.itilms.assessment.entity.Submission;
import com.itilms.assessment.entity.SubmissionStatus;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    Optional<Submission> findByAssignmentIdAndStudentId(Long assignmentId, Long studentId);

    List<Submission> findByAssignmentIdOrderBySubmittedAtAsc(Long assignmentId);

    List<Submission> findByStudentIdOrderBySubmittedAtDesc(Long studentId);

    List<Submission> findByStudentIdAndAssignmentIdIn(Long studentId, Collection<Long> assignmentIds);

    long countByAssignmentIdAndStatusIn(Long assignmentId, Collection<SubmissionStatus> statuses);

    /**
     * Submitted-and-evaluated counts for many assignments at once.
     *
     * <p>An assignment listing shows both numbers per row. Asking per row would
     * be two queries per assignment; this is one for the page.
     */
    @Query("""
            SELECT s.assignmentId,
                   COUNT(s),
                   SUM(CASE WHEN s.status = com.itilms.assessment.entity.SubmissionStatus.EVALUATED
                            THEN 1 ELSE 0 END)
            FROM Submission s
            WHERE s.assignmentId IN :assignmentIds
            GROUP BY s.assignmentId
            """)
    List<Object[]> countsByAssignment(@Param("assignmentIds") Collection<Long> assignmentIds);

    /** A trainer's marking queue. */
    @Query("""
            SELECT s FROM Submission s
            WHERE s.assignmentId IN :assignmentIds
              AND s.status IN (com.itilms.assessment.entity.SubmissionStatus.SUBMITTED,
                               com.itilms.assessment.entity.SubmissionStatus.LATE)
            ORDER BY s.submittedAt ASC
            """)
    List<Submission> findAwaitingEvaluation(@Param("assignmentIds") Collection<Long> assignmentIds);

    /** How much of the mandatory work a student has finished (Doc S7.3). */
    @Query("""
            SELECT COUNT(s) FROM Submission s
            WHERE s.studentId = :studentId
              AND s.assignmentId IN :assignmentIds
              AND s.status = com.itilms.assessment.entity.SubmissionStatus.EVALUATED
            """)
    long countEvaluatedFor(@Param("studentId") Long studentId,
                           @Param("assignmentIds") Collection<Long> assignmentIds);
}
