package com.itilms.batch.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.itilms.batch.entity.ClassSession;

@Repository
public interface ClassSessionRepository extends JpaRepository<ClassSession, Long> {

    List<ClassSession> findByBatchIdOrderBySessionDateAscStartTimeAsc(Long batchId);

    boolean existsByBatchIdAndSessionDateAndStartTime(Long batchId, LocalDate date,
                                                      java.time.LocalTime startTime);

    /**
     * The timetable for a set of batches over a date range.
     *
     * <p>One query serves every role: a student passes their own batch ids, a
     * trainer passes theirs, an admin passes all of them. Keeping the filtering
     * in the caller — which knows what the caller is allowed to see — avoids a
     * role check buried in a query.
     */
    @Query("""
            SELECT s FROM ClassSession s
             WHERE s.batchId IN :batchIds
               AND s.sessionDate BETWEEN :from AND :to
             ORDER BY s.sessionDate, s.startTime
            """)
    List<ClassSession> findTimetable(@Param("batchIds") Collection<Long> batchIds,
                                     @Param("from") LocalDate from,
                                     @Param("to") LocalDate to);

    @Query("""
            SELECT s FROM ClassSession s
             WHERE s.trainerId = :trainerId
               AND s.sessionDate BETWEEN :from AND :to
             ORDER BY s.sessionDate, s.startTime
            """)
    List<ClassSession> findTrainerTimetable(@Param("trainerId") Long trainerId,
                                            @Param("from") LocalDate from,
                                            @Param("to") LocalDate to);

    /** Today's classes — the first thing on every dashboard (Doc S15). */
    @Query("""
            SELECT s FROM ClassSession s
             WHERE s.sessionDate = :today AND s.status = 'SCHEDULED'
               AND (:trainerId IS NULL OR s.trainerId = :trainerId)
             ORDER BY s.startTime
            """)
    List<ClassSession> findTodaysSessions(@Param("today") LocalDate today,
                                          @Param("trainerId") Long trainerId);

    /**
     * Finished classes whose register was never filled in.
     *
     * <p>Doc S15 puts "attendance pending" on the coordinator dashboard. Bounded
     * by a start date so the list is a worklist, not an archive of every session
     * since the institute opened.
     */
    @Query("""
            SELECT s FROM ClassSession s
             WHERE s.attendanceMarked = false
               AND s.status = 'SCHEDULED'
               AND s.sessionDate BETWEEN :from AND :to
               AND (:trainerId IS NULL OR s.trainerId = :trainerId)
             ORDER BY s.sessionDate DESC, s.startTime DESC
            """)
    List<ClassSession> findPendingAttendance(@Param("from") LocalDate from,
                                             @Param("to") LocalDate to,
                                             @Param("trainerId") Long trainerId);

    /**
     * Sessions a live room should be ready for.
     *
     * <p>liveclass-service polls this so a trainer opening a class at 10:00
     * finds the room already there, rather than waiting for it to be created.
     */
    @Query("""
            SELECT s FROM ClassSession s
             WHERE s.sessionDate = :date
               AND s.status = 'SCHEDULED'
               AND s.mode IN ('ONLINE','HYBRID')
             ORDER BY s.startTime
            """)
    List<ClassSession> findOnlineSessionsOn(@Param("date") LocalDate date);

    /** How many sessions a batch has actually held — the attendance denominator. */
    @Query("""
            SELECT COUNT(s) FROM ClassSession s
             WHERE s.batchId = :batchId AND s.attendanceMarked = true AND s.status <> 'CANCELLED'
            """)
    long countMarkedSessions(@Param("batchId") Long batchId);

    @Query("SELECT s.batchId FROM ClassSession s WHERE s.id = :sessionId")
    java.util.Optional<Long> findBatchIdById(@Param("sessionId") Long sessionId);
}
