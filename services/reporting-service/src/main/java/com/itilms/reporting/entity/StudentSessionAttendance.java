package com.itilms.reporting.entity;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The current status of one student in one session, mirrored from
 * {@code AttendanceMarkedEvent} for the dashboard (Doc S15). Keyed by
 * (student, session) rather than incremented as counts: batch-service can
 * correct a marking (Doc S6.9, S14), and a correction is just a second event
 * for the same session - upserting here means a correction is applied
 * automatically instead of double-counting the old and new status.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "student_session_attendance")
public class StudentSessionAttendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    /** PRESENT, ABSENT, LATE or EXCUSED (Doc S6.9, R8 in docs/02). */
    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "marked_at", nullable = false)
    private Instant markedAt;
}
