package com.itilms.liveclass.entity;

import com.itilms.common.entity.AuditableEntity;

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

/** One monitoring setting, at one level. See {@code V5__student_monitoring.sql}. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "monitoring_settings")
public class MonitoringSetting extends AuditableEntity {

    public enum Scope {
        INSTITUTE, COURSE, BATCH, SESSION
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 12)
    private Scope scopeType;

    @Column(name = "scope_id", nullable = false)
    private Long scopeId;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "face_visibility", nullable = false)
    private boolean faceVisibility;

    @Column(name = "camera_required", nullable = false)
    private boolean cameraRequired;

    /** The student must allow the microphone to join; permission only, no listening. */
    @Column(name = "microphone_required", nullable = false)
    private boolean microphoneRequired;

    @Column(name = "warning_after_seconds", nullable = false)
    private int warningAfterSeconds;

    @Column(name = "show_warning", nullable = false)
    private boolean showWarning;

    @Column(name = "warning_message", length = 200)
    private String warningMessage;

    @Column(name = "log_events", nullable = false)
    private boolean logEvents;

    /** Note when the student leaves the class tab or window. */
    @Column(name = "tab_switch_check", nullable = false)
    private boolean tabSwitchCheck;

    /** Seconds with the face missing or the tab left are taken off the student's attended time. */
    @Column(name = "attendance_penalty", nullable = false)
    private boolean attendancePenalty;

    /** More than one face, or a missing face, is CRITICAL straight away. */
    @Column(name = "strict_severity", nullable = false)
    private boolean strictSeverity;

    /** Remove the student from the room after {@link #autoRemoveAfterCritical} CRITICAL notes. */
    @Column(name = "auto_remove", nullable = false)
    private boolean autoRemove;

    @Column(name = "auto_remove_after_critical", nullable = false)
    @Builder.Default
    private int autoRemoveAfterCritical = 3;

    /** The trainer's panel raises an alert for each CRITICAL note. */
    @Column(name = "alert_trainer", nullable = false)
    private boolean alertTrainer;

    @Column(name = "updated_by")
    private Long updatedBy;
}
