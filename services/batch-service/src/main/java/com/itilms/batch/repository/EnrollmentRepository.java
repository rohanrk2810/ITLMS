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

    List<Enrollment> findByStudentId(Long studentId);

    Optional<Enrollment> findByStudentIdAndBatchIdAndStatus(Long studentId, Long batchId,
                                                            EnrollmentStatus status);

    /** Doc S14: no second active enrolment in the same batch. */
    boolean existsByStudentIdAndBatchIdAndStatus(Long studentId, Long batchId, EnrollmentStatus status);

    /** Capacity check (Doc S6.6): how many seats are taken. */
    long countByBatchIdAndStatus(Long batchId, EnrollmentStatus status);

    long countByStatus(EnrollmentStatus status);

    /** The register for a session: active students in the batch, in name order. */
    @Query("""
            SELECT e FROM Enrollment e
             WHERE e.batchId = :batchId AND e.status = 'ACTIVE'
             ORDER BY e.studentName
            """)
    List<Enrollment> findActiveRoster(@Param("batchId") Long batchId);

    @Query("SELECT e.studentId FROM Enrollment e WHERE e.batchId = :batchId AND e.status = 'ACTIVE'")
    List<Long> findActiveStudentIds(@Param("batchId") Long batchId);

    @Query("SELECT e.userId FROM Enrollment e WHERE e.batchId = :batchId AND e.status = 'ACTIVE'")
    List<Long> findActiveUserIds(@Param("batchId") Long batchId);

    /** Seats taken per batch, for a listing that shows capacity without N queries. */
    @Query("""
            SELECT e.batchId, COUNT(e) FROM Enrollment e
             WHERE e.batchId IN :batchIds AND e.status = 'ACTIVE'
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
