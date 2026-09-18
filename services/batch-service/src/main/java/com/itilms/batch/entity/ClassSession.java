package com.itilms.batch.entity;

import java.time.LocalDate;
import java.time.LocalTime;

import com.itilms.common.entity.AuditableEntity;

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

/** One scheduled lecture (Doc S6.7). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "class_sessions")
public class ClassSession extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "trainer_id")
    private Long trainerId;

    @Column(name = "trainer_user_id")
    private Long trainerUserId;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(length = 255)
    private String topic;

    /**
     * How this particular session runs.
     *
     * <p>Inherited from the batch but overridable: an otherwise classroom-based
     * batch may hold one session online when the trainer is travelling, and that
     * session needs a live room even though the batch does not.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private BatchMode mode = BatchMode.OFFLINE;

    @Column(name = "meeting_url", length = 600)
    private String meetingUrl;

    @Column(length = 60)
    private String room;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SessionStatus status = SessionStatus.SCHEDULED;

    @Column(name = "attendance_marked", nullable = false)
    @Builder.Default
    private boolean attendanceMarked = false;

    /** True when the register was filled in from live-room activity. */
    @Column(name = "attendance_auto", nullable = false)
    @Builder.Default
    private boolean attendanceAuto = false;

    @Column(name = "cancelled_reason", length = 255)
    private String cancelledReason;

    public int durationMinutes() {
        return (int) java.time.Duration.between(startTime, endTime).toMinutes();
    }

    /** True once the scheduled end time has passed. */
    public boolean hasFinished() {
        return sessionDate.atTime(endTime)
                .isBefore(java.time.LocalDateTime.now());
    }

    public void cancel(String reason) {
        this.status = SessionStatus.CANCELLED;
        this.cancelledReason = reason;
    }
}
