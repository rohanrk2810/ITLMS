package com.itilms.liveclass.dto;

import java.time.Instant;

import com.itilms.liveclass.entity.MonitoringEvent;
import com.itilms.liveclass.entity.MonitoringSetting;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request and response shapes for student monitoring. */
public final class MonitoringDtos {

    private MonitoringDtos() {
    }

    public record SettingRequest(
            @NotNull MonitoringSetting.Scope scopeType,
            /** Ignored for INSTITUTE. Otherwise the course id, batch id or timetable session id. */
            Long scopeId,
            @NotNull Boolean enabled,
            Boolean faceVisibility,
            Boolean cameraRequired,
            @Min(3) @Max(300) Integer warningAfterSeconds,
            Boolean showWarning,
            @Size(max = 200) String warningMessage,
            Boolean logEvents,
            /** The student must allow the microphone to join. Permission only: nothing is listened to. */
            Boolean microphoneRequired,
            /** Each of these is its own switch; left out means off. */
            Boolean tabSwitchCheck,
            Boolean attendancePenalty,
            Boolean strictSeverity,
            Boolean autoRemove,
            @Min(1) @Max(20) Integer autoRemoveAfterCritical,
            Boolean alertTrainer) {

        /** A setting that says nothing about the microphone or the stricter options (they stay off). */
        public SettingRequest(MonitoringSetting.Scope scopeType, Long scopeId, Boolean enabled, Boolean faceVisibility,
                              Boolean cameraRequired, Integer warningAfterSeconds, Boolean showWarning,
                              String warningMessage, Boolean logEvents) {
            this(scopeType, scopeId, enabled, faceVisibility, cameraRequired, warningAfterSeconds, showWarning,
                    warningMessage, logEvents, null);
        }

        public SettingRequest(MonitoringSetting.Scope scopeType, Long scopeId, Boolean enabled, Boolean faceVisibility,
                              Boolean cameraRequired, Integer warningAfterSeconds, Boolean showWarning,
                              String warningMessage, Boolean logEvents, Boolean microphoneRequired) {
            this(scopeType, scopeId, enabled, faceVisibility, cameraRequired, warningAfterSeconds, showWarning,
                    warningMessage, logEvents, microphoneRequired, null, null, null, null, null, null);
        }
    }

    public record SettingResponse(Long id, String scopeType, Long scopeId, boolean enabled, boolean faceVisibility,
                                  boolean cameraRequired, boolean microphoneRequired, int warningAfterSeconds, boolean showWarning,
                                  String warningMessage, boolean logEvents, Instant updatedAt,
                                  boolean tabSwitchCheck, boolean attendancePenalty, boolean strictSeverity,
                                  boolean autoRemove, int autoRemoveAfterCritical, boolean alertTrainer) {

        public static SettingResponse from(MonitoringSetting s) {
            return new SettingResponse(s.getId(), s.getScopeType().name(), s.getScopeId(), s.isEnabled(),
                    s.isFaceVisibility(), s.isCameraRequired(), s.isMicrophoneRequired(), s.getWarningAfterSeconds(), s.isShowWarning(),
                    s.getWarningMessage(), s.isLogEvents(), s.getUpdatedAt(),
                    s.isTabSwitchCheck(), s.isAttendancePenalty(), s.isStrictSeverity(), s.isAutoRemove(),
                    s.getAutoRemoveAfterCritical(), s.isAlertTrainer());
        }
    }

    /** What applies to one class, and where that came from. */
    public record EffectiveResponse(boolean enabled, boolean faceVisibility, boolean cameraRequired,
                                    boolean microphoneRequired, int warningAfterSeconds, boolean showWarning, String warningMessage,
                                    boolean logEvents, String source,
                                    boolean tabSwitchCheck, boolean attendancePenalty, boolean strictSeverity,
                                    boolean autoRemove, int autoRemoveAfterCritical, boolean alertTrainer) {
    }

    /** One student's monitoring summary for a class. */
    public record StudentReport(Long studentId, String studentName,
                                int faceAbsences, int faceAbsentSeconds,
                                int tabSwitches, int tabAwaySeconds,
                                int multipleFaces, int cameraProblems,
                                int warnings, int critical, boolean removed, int penaltySeconds) {
    }

    public record EventRequest(
            @NotNull MonitoringEvent.Type type,
            @Min(0) @Max(86400) Integer durationSeconds,
            @Size(max = 255) String detail) {
    }

    public record EventResponse(Long id, Long studentId, String studentName, String type, String severity,
                                Instant occurredAt, int offsetSeconds, String offsetLabel,
                                Integer durationSeconds, String detail) {

        public static EventResponse from(MonitoringEvent e, String offsetLabel) {
            return new EventResponse(e.getId(), e.getStudentId(), e.getStudentName(), e.getEventType().name(),
                    e.getSeverity().name(), e.getOccurredAt(), e.getOffsetSeconds(), offsetLabel,
                    e.getDurationSeconds(), e.getDetail());
        }
    }
}
