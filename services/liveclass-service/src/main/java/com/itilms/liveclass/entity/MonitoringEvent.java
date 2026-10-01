package com.itilms.liveclass.entity;

import java.time.Instant;

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

/** Something the monitoring noticed about one student during one class. No pictures are ever stored. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "monitoring_events")
public class MonitoringEvent {

    public enum Type {
        FACE_NOT_DETECTED, FACE_RESTORED, MULTIPLE_FACES, CAMERA_DISABLED, CAMERA_PERMISSION_DENIED
    }

    public enum Severity {
        INFO, WARNING, CRITICAL
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "live_session_id", nullable = false)
    private Long liveSessionId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "student_user_id", nullable = false)
    private Long studentUserId;

    @Column(name = "student_name", length = 160)
    private String studentName;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private Type eventType;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Severity severity;

    @Column(name = "occurred_at", nullable = false)
    @Builder.Default
    private Instant occurredAt = Instant.now();

    @Column(name = "offset_seconds", nullable = false)
    private int offsetSeconds;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(length = 255)
    private String detail;
}
