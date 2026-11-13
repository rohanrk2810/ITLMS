package com.itilms.batch.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.itilms.batch.entity.Enrollment;
import com.itilms.batch.entity.EnrollmentStatus;

@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, Long>,
        JpaSpecificationExecutor<Enrollment> {

    List<Enrollment> findByBatchIdAndStatus(Long batchId, EnrollmentStatus status);

    List<Enrollment> findByBatchId(Long batchId);

    /** Active enrolments whose access end date is before the given day: the nightly job closes them. */
    List<Enrollment> findByStatusAndAccessUntilBefore(EnrollmentStatus status, java.time.LocalDate day);

    List<Enrollment> findByStudentId(Long studentId);

    /** Whether the student has ever been placed in a batch, whatever became of that place. */
    boolean existsByStudentId(Long studentId);

    Optional<Enrollment> findByStudentIdAndBatchIdAndStatus(Long studentId, Long batchId,
                                                            EnrollmentStatus status);

    /** Doc S14: no second active enrolment in the same batch. */
    boolean existsByStudentIdAndBatchIdAndStatus(Long studentId, Long batchId, EnrollmentStatus status);

    /** Capacity check (Doc S6.6): how many seats are taken. A course that came with the batch is not a seat. */
    @Query("SELECT COUNT(e) FROM Enrollment e WHERE e.batchId = :batchId AND e.status = :status AND e.included = false")
    long countByBatchIdAndStatus(@Param("batchId") Long batchId, @Param("status") EnrollmentStatus status);

    long countByStatus(EnrollmentStatus status);

    /** The register for a session: active students in the batch, in name order. */
    @Query("""
            SELECT e FROM Enrollment e
             WHERE e.batchId = :batchId AND e.status = 'ACTIVE' AND e.included = false
             ORDER BY e.studentName
            """)
    List<Enrollment> findActiveRoster(@Param("batchId") Long batchId);

    /**
     * Who is on the register of a lecture: the students who have that lecture's course in the batch, whether they
     * bought it or it came with the batch. A lecture with no course of its own is for the course the batch was
     * created for.
     */
    @Query("""
            SELECT e.studentId FROM Enrollment e
             WHERE e.batchId = :batchId AND e.status = 'ACTIVE'
               AND e.courseId = COALESCE(:courseId, (SELECT b.courseId FROM Batch b WHERE b.id = e.batchId))
            """)
    List<Long> findRegisterStudentIds(@Param("batchId") Long batchId, @Param("courseId") Long courseId);

    /** The courses a student holds in the batch because it came with the placement. */
    List<Enrollment> findByStudentIdAndBatchIdAndIncludedTrueAndStatus(Long studentId, Long batchId, EnrollmentStatus status);

    /** Every whole-batch placement (the rows that carry the batch fee), for adding a course to all of them. */
    @Query("""
            SELECT e FROM Enrollment e
             WHERE e.batchId = :batchId AND e.status = 'ACTIVE' AND e.included = false AND e.courseOnly = false
               AND e.courseId = :courseId
            """)
    List<Enrollment> findWholeBatchPlacements(@Param("batchId") Long batchId, @Param("courseId") Long courseId);

    /** The included rows of one course in a batch (to close them when the course is taken out). */
    List<Enrollment> findByBatchIdAndCourseIdAndIncludedTrueAndStatus(Long batchId, Long courseId, EnrollmentStatus status);

    @Query("SELECT e.studentId FROM Enrollment e WHERE e.batchId = :batchId AND e.status = 'ACTIVE' AND e.included = false")
    List<Long> findActiveStudentIds(@Param("batchId") Long batchId);

    @Query("SELECT e.userId FROM Enrollment e WHERE e.batchId = :batchId AND e.status = 'ACTIVE' AND e.included = false")
    List<Long> findActiveUserIds(@Param("batchId") Long batchId);

    /** Seats taken per batch, for a listing that shows capacity without N queries. */
    @Query("""
            SELECT e.batchId, COUNT(e) FROM Enrollment e
             WHERE e.batchId IN :batchIds AND e.status = 'ACTIVE' AND e.included = false
             GROUP BY e.batchId
            """)
    List<Object[]> countActiveGroupedByBatch(@Param("batchIds") Collection<Long> batchIds);

    /** Every batch a student is currently in — their "My batches" page. */
    @Query("""
            SELECT e FROM Enrollment e
             WHERE e.studentId = :studentId AND e.status = 'ACTIVE'
             ORDER BY e.enrolledAt DESC
            """)
    List<Enrollment> findActiveForStudent(@Param("studentId") Long studentId);
}
