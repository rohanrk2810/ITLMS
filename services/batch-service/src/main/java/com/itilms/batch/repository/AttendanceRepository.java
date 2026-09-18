package com.itilms.batch.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.itilms.batch.entity.Attendance;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    List<Attendance> findBySessionId(Long sessionId);

    Optional<Attendance> findBySessionIdAndStudentId(Long sessionId, Long studentId);

    List<Attendance> findBySessionIdIn(Collection<Long> sessionIds);

    void deleteBySessionId(Long sessionId);

    /**
     * A student's attendance percentage in one batch.
     *
     * <p>PRESENT and LATE both count as attended; EXCUSED is removed from the
     * denominator entirely, so an approved absence neither helps nor harms.
     * Returns {@code [attended, total]}.
     */
    @Query("""
            SELECT
              SUM(CASE WHEN a.status IN ('PRESENT','LATE') THEN 1 ELSE 0 END),
              SUM(CASE WHEN a.status <> 'EXCUSED' THEN 1 ELSE 0 END)
            FROM Attendance a
            WHERE a.studentId = :studentId
              AND a.sessionId IN (SELECT s.id FROM ClassSession s
                                   WHERE s.batchId = :batchId AND s.status <> 'CANCELLED')
            """)
    Object[] attendanceTotalsForBatch(@Param("studentId") Long studentId,
                                      @Param("batchId") Long batchId);

    /**
     * The same figures for every student in a batch, in one query.
     *
     * <p>Returns {@code [studentId, attended, total]} rows. The register view
     * shows forty students at once; running the single-student query forty times
     * is the classic way a page that felt fine in testing falls over in a real
     * classroom.
     */
    @Query("""
            SELECT a.studentId,
                   SUM(CASE WHEN a.status IN ('PRESENT','LATE') THEN 1 ELSE 0 END),
                   SUM(CASE WHEN a.status <> 'EXCUSED' THEN 1 ELSE 0 END)
              FROM Attendance a
             WHERE a.sessionId IN (SELECT s.id FROM ClassSession s
                                    WHERE s.batchId = :batchId AND s.status <> 'CANCELLED')
             GROUP BY a.studentId
            """)
    List<Object[]> attendanceTotalsForAllInBatch(@Param("batchId") Long batchId);

    /** A student's own attendance history, newest first (Doc S8.2). */
    @Query("""
            SELECT a, s FROM Attendance a JOIN ClassSession s ON s.id = a.sessionId
             WHERE a.studentId = :studentId
               AND (:batchId IS NULL OR s.batchId = :batchId)
               AND s.sessionDate BETWEEN :from AND :to
             ORDER BY s.sessionDate DESC, s.startTime DESC
            """)
    List<Object[]> findStudentHistory(@Param("studentId") Long studentId,
                                      @Param("batchId") Long batchId,
                                      @Param("from") LocalDate from,
                                      @Param("to") LocalDate to);

    /**
     * Students whose attendance has fallen below a threshold.
     *
     * <p>Feeds the "attendance alerts" widget on the trainer dashboard
     * (Doc S15) and the certificate criteria check (Doc S7.3). Catching this
     * early is the difference between a conversation and a failed certification.
     */
    @Query("""
            SELECT a.studentId,
                   SUM(CASE WHEN a.status IN ('PRESENT','LATE') THEN 1 ELSE 0 END) * 100.0
                     / NULLIF(SUM(CASE WHEN a.status <> 'EXCUSED' THEN 1 ELSE 0 END), 0)
              FROM Attendance a
             WHERE a.sessionId IN (SELECT s.id FROM ClassSession s
                                    WHERE s.batchId = :batchId AND s.status <> 'CANCELLED')
             GROUP BY a.studentId
            HAVING SUM(CASE WHEN a.status IN ('PRESENT','LATE') THEN 1 ELSE 0 END) * 100.0
                     / NULLIF(SUM(CASE WHEN a.status <> 'EXCUSED' THEN 1 ELSE 0 END), 0) < :threshold
            """)
    List<Object[]> findBelowThreshold(@Param("batchId") Long batchId,
                                      @Param("threshold") double threshold);
}
