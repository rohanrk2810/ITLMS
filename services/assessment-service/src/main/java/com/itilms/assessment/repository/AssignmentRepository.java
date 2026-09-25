package com.itilms.assessment.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.itilms.assessment.entity.Assignment;
import com.itilms.assessment.entity.AssignmentStatus;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    Page<Assignment> findByBatchIdOrderByDueAtDesc(Long batchId, Pageable pageable);

    List<Assignment> findByBatchIdInAndStatusInOrderByDueAtAsc(Collection<Long> batchIds, Collection<AssignmentStatus> statuses);

    /** What a student sees: published work for the batches they are in. */
    List<Assignment> findByBatchIdInAndStatusOrderByDueAtAsc(Collection<Long> batchIds, AssignmentStatus status);

    long countByBatchIdAndStatus(Long batchId, AssignmentStatus status);

    /**
     * Published work falling due in a window.
     *
     * <p>Feeds the "due soon" reminder (Doc S16) and the student dashboard's
     * pending-work count (Doc S15).
     */
    @Query("""
            SELECT a FROM Assignment a
            WHERE a.status = com.itilms.assessment.entity.AssignmentStatus.PUBLISHED
              AND a.dueAt BETWEEN :from AND :to
            ORDER BY a.dueAt ASC
            """)
    List<Assignment> findDueBetween(@Param("from") Instant from, @Param("to") Instant to);

    /** Mandatory work for a course - the completion check in Doc S7.3. */
    @Query("""
            SELECT a FROM Assignment a
            WHERE a.courseId = :courseId
              AND a.mandatory = true
              AND a.status <> com.itilms.assessment.entity.AssignmentStatus.DRAFT
            """)
    List<Assignment> findMandatoryForCourse(@Param("courseId") Long courseId);

    /**
     * Mandatory work set for one batch.
     *
     * <p>Completion is judged per batch, not per course: a student in the
     * weekday batch is not held to the assignments the weekend batch was set.
     */
    @Query("""
            SELECT a FROM Assignment a
            WHERE a.batchId = :batchId
              AND a.mandatory = true
              AND a.status <> com.itilms.assessment.entity.AssignmentStatus.DRAFT
            """)
    List<Assignment> findMandatoryForBatch(@Param("batchId") Long batchId);
}
