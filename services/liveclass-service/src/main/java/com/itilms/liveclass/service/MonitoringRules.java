package com.itilms.liveclass.service;

import java.util.List;

import com.itilms.liveclass.entity.MonitoringEvent;
import com.itilms.liveclass.entity.MonitoringSetting;

/** The two pure decisions in monitoring: which setting applies, and how serious an event is. */
public final class MonitoringRules {

    /** A face gone this long is no longer "stepped away for a moment". */
    static final int CRITICAL_AFTER_SECONDS = 60;

    private MonitoringRules() {
    }

    /** What applies to a class when no setting exists at any level: nothing is monitored. */
    public static final Effective OFF = new Effective(false, true, false, false, 10, true, null, true, "DEFAULT");

    /** The settings that apply to one class, as a student's browser and the event check need them. */
    public record Effective(boolean enabled, boolean faceVisibility, boolean cameraRequired, boolean microphoneRequired,
                            int warningAfterSeconds, boolean showWarning, String warningMessage,
                            boolean logEvents, String source) {

        static Effective of(MonitoringSetting s) {
            return new Effective(s.isEnabled(), s.isFaceVisibility(), s.isCameraRequired(), s.isMicrophoneRequired(),
                    s.getWarningAfterSeconds(),
                    s.isShowWarning(), s.getWarningMessage(), s.isLogEvents(), s.getScopeType().name());
        }
    }

    /**
     * The most specific setting wins: class, then batch, then course, then the institute.
     *
     * @param mostSpecificFirst the settings found for this class, in that order; missing levels are simply absent
     */
    public static Effective resolve(List<MonitoringSetting> mostSpecificFirst) {
        return mostSpecificFirst.stream().findFirst().map(Effective::of).orElse(OFF);
    }

    public static MonitoringEvent.Severity severity(MonitoringEvent.Type type, Integer durationSeconds) {
        return switch (type) {
            case FACE_RESTORED -> MonitoringEvent.Severity.INFO;
            case MULTIPLE_FACES -> MonitoringEvent.Severity.WARNING;
            case FACE_NOT_DETECTED -> durationSeconds != null && durationSeconds >= CRITICAL_AFTER_SECONDS
                    ? MonitoringEvent.Severity.CRITICAL : MonitoringEvent.Severity.WARNING;
            case CAMERA_DISABLED, CAMERA_PERMISSION_DENIED -> MonitoringEvent.Severity.CRITICAL;
            case MICROPHONE_DISABLED, MICROPHONE_PERMISSION_DENIED -> MonitoringEvent.Severity.WARNING;
        };
    }

    /** Face checks only run when face visibility is on; camera problems are reported whenever monitoring is on. */
    public static boolean accepts(Effective effective, MonitoringEvent.Type type) {
        if (!effective.enabled() || !effective.logEvents()) {
            return false;
        }
        // The microphone is only watched where an administrator made it a requirement.
        if (type == MonitoringEvent.Type.MICROPHONE_DISABLED || type == MonitoringEvent.Type.MICROPHONE_PERMISSION_DENIED) {
            return effective.microphoneRequired();
        }
        boolean faceType = type == MonitoringEvent.Type.FACE_NOT_DETECTED
                || type == MonitoringEvent.Type.FACE_RESTORED || type == MonitoringEvent.Type.MULTIPLE_FACES;
        return !faceType || effective.faceVisibility();
    }
}
