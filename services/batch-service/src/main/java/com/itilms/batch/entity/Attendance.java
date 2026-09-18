package com.itilms.batch.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * One student's attendance for one session (Doc S6.9).
 *
 * <p>Corrections overwrite the status but keep {@code markedBy} and
 * {@code markedAt} alongside {@code correctedBy} and {@code correctedAt}. Doc
 * S14 requires attendance corrections to be audited, and the useful audit
 * question is not just "who changed it" but "who said otherwise first".
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "attendance")
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttendanceStatus status;

    @Column(length = 255)
    private String remark;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AttendanceSource source = AttendanceSource.MANUAL;

    /** Minutes in the live room. Null for classroom sessions. */
    @Column(name = "attended_minutes")
    private Integer attendedMinutes;

    @Column(name = "marked_at", nullable = false)
    @Builder.Default
    private Instant markedAt = Instant.now();

    @Column(name = "marked_by")
    private Long markedBy;

    @Column(name = "corrected_at")
    private Instant correctedAt;

    @Column(name = "corrected_by")
    private Long correctedBy;

    /**
     * Applies a correction, preserving the original marking.
     *
     * <p>A corrected record always becomes MANUAL: once a human has overruled
     * the automatic figure, later live-class recomputation must not silently
     * put it back.
     */
    public void correct(AttendanceStatus newStatus, String remark, Long correctedByUserId) {
        this.status = newStatus;
        this.remark = remark;
        this.source = AttendanceSource.MANUAL;
        this.correctedAt = Instant.now();
        this.correctedBy = correctedByUserId;
    }

    public boolean wasCorrected() {
        return correctedAt != null;
    }
}
