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

    @Column(name = "warning_after_seconds", nullable = false)
    private int warningAfterSeconds;

    @Column(name = "show_warning", nullable = false)
    private boolean showWarning;

    @Column(name = "warning_message", length = 200)
    private String warningMessage;

    @Column(name = "log_events", nullable = false)
    private boolean logEvents;

    @Column(name = "updated_by")
    private Long updatedBy;
}
