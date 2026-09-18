package com.itilms.batch.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.itilms.batch.entity.Batch;
import com.itilms.batch.entity.BatchStatus;

@Repository
public interface BatchRepository extends JpaRepository<Batch, Long>, JpaSpecificationExecutor<Batch> {

    Optional<Batch> findByBatchCodeIgnoreCase(String batchCode);

    boolean existsByBatchCodeIgnoreCase(String batchCode);

    List<Batch> findByIdIn(Collection<Long> ids);

    List<Batch> findByTrainerIdAndStatusIn(Long trainerId, Collection<BatchStatus> statuses);

    long countByStatus(BatchStatus status);

    /** Per-course batch number, used to build {@code JFS-2026-B03}. */
    @Query("SELECT COUNT(b) + 1 FROM Batch b WHERE b.courseId = :courseId")
    long nextSequenceForCourse(@Param("courseId") Long courseId);

    /**
     * Batches due to start or finish, for the nightly status sweep.
     *
     * <p>A batch whose start date has arrived should be ONGOING without anyone
     * having to remember to change it, and one past its end date should be
     * COMPLETED — otherwise "active batches" on the admin dashboard slowly turns
     * into "batches somebody once created".
     */
    @Query("""
            SELECT b FROM Batch b
             WHERE (b.status = 'PLANNED' AND b.startDate <= :today)
                OR (b.status = 'ONGOING' AND b.endDate IS NOT NULL AND b.endDate < :today)
            """)
    List<Batch> findNeedingStatusTransition(@Param("today") LocalDate today);

    /** Refreshes the copied course title after course-service reports a change. */
    @Modifying
    @Query("""
            UPDATE Batch b SET b.courseTitle = :title, b.courseCode = :code
             WHERE b.courseId = :courseId
            """)
    int syncCourse(@Param("courseId") Long courseId,
                   @Param("title") String title,
                   @Param("code") String code);

    @Modifying
    @Query("UPDATE Batch b SET b.trainerName = :name WHERE b.trainerId = :trainerId")
    int syncTrainerName(@Param("trainerId") Long trainerId, @Param("name") String name);

    @Query("SELECT b.id FROM Batch b WHERE b.status IN ('PLANNED','ONGOING')")
    List<Long> findActiveBatchIds();
}
