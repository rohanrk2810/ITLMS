package com.itilms.reporting.repository;

import java.time.Instant;
import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.itilms.reporting.entity.StudentSessionAttendance;

@Repository
public interface StudentSessionAttendanceRepository extends JpaRepository<StudentSessionAttendance, Long> {

    /**
     * Insert the first marking for a session, or overwrite it on a correction
     * (Doc S6.9, S14). Upserting the current status - rather than incrementing
     * present/absent counters - is what makes a correction come out right
     * without reporting-service needing to know what the old status was.
     */
    @Modifying
    @Query(value = """
            INSERT INTO student_session_attendance (student_id, session_id, batch_id, session_date, status, marked_at)
            VALUES (:studentId, :sessionId, :batchId, :sessionDate, :status, :markedAt)
            ON CONFLICT (student_id, session_id)
            DO UPDATE SET status = EXCLUDED.status, batch_id = EXCLUDED.batch_id,
                          session_date = EXCLUDED.session_date, marked_at = EXCLUDED.marked_at
            """, nativeQuery = true)
    void upsert(@Param("studentId") Long studentId, @Param("sessionId") Long sessionId,
               @Param("batchId") Long batchId, @Param("sessionDate") LocalDate sessionDate,
               @Param("status") String status, @Param("markedAt") Instant markedAt);

    long countByStatus(String status);

    long countByStudentIdAndStatus(Long studentId, String status);
}
