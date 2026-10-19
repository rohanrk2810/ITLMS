package com.itilms.liveclass.service;

import java.util.List;

import com.itilms.liveclass.entity.MonitoringEvent;
import com.itilms.liveclass.entity.MonitoringSetting;

/** The pure decisions in monitoring: which setting applies, how serious an event is, what a student loses. */
public final class MonitoringRules {

    /** A face gone this long is no longer "stepped away for a moment". */
    static final int CRITICAL_AFTER_SECONDS = 60;
    /** Away from the class tab this long is serious; half that when strict severity is on. */
    static final int TAB_CRITICAL_AFTER_SECONDS = 60;
    static final int STRICT_TAB_CRITICAL_AFTER_SECONDS = 30;

    private MonitoringRules() {
    }

    /** What applies to a class when no setting exists at any level: nothing is monitored. */
    public static final Effective OFF = new Effective(false, true, false, false, 10, true, null, true, "DEFAULT",
            false, false, false, false, 3, false);

    /** The settings that apply to one class, as a student's browser and the event check need them. */
    public record Effective(boolean enabled, boolean faceVisibility, boolean cameraRequired, boolean microphoneRequired,
                            int warningAfterSeconds, boolean showWarning, String warningMessage,
                            boolean logEvents, String source,
                            boolean tabSwitchCheck, boolean attendancePenalty, boolean strictSeverity,
                            boolean autoRemove, int autoRemoveAfterCritical, boolean alertTrainer) {

        static Effective of(MonitoringSetting s) {
            return new Effective(s.isEnabled(), s.isFaceVisibility(), s.isCameraRequired(), s.isMicrophoneRequired(),
                    s.getWarningAfterSeconds(),
                    s.isShowWarning(), s.getWarningMessage(), s.isLogEvents(), s.getScopeType().name(),
                    s.isTabSwitchCheck(), s.isAttendancePenalty(), s.isStrictSeverity(), s.isAutoRemove(),
                    s.getAutoRemoveAfterCritical(), s.isAlertTrainer());
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
        return severity(type, durationSeconds, false);
    }

    /** With {@code strict}, more than one face and a missing face are CRITICAL at once, and a tab left sooner is. */
    public static MonitoringEvent.Severity severity(MonitoringEvent.Type type, Integer durationSeconds, boolean strict) {
        return switch (type) {
            case FACE_RESTORED -> MonitoringEvent.Severity.INFO;
            case MULTIPLE_FACES -> strict ? MonitoringEvent.Severity.CRITICAL : MonitoringEvent.Severity.WARNING;
            case FACE_NOT_DETECTED -> strict || (durationSeconds != null && durationSeconds >= CRITICAL_AFTER_SECONDS)
                    ? MonitoringEvent.Severity.CRITICAL : MonitoringEvent.Severity.WARNING;
            case CAMERA_DISABLED, CAMERA_PERMISSION_DENIED, REMOVED_BY_MONITORING -> MonitoringEvent.Severity.CRITICAL;
            case MICROPHONE_DISABLED, MICROPHONE_PERMISSION_DENIED -> MonitoringEvent.Severity.WARNING;
            case TAB_AWAY -> durationSeconds != null && durationSeconds
                    >= (strict ? STRICT_TAB_CRITICAL_AFTER_SECONDS : TAB_CRITICAL_AFTER_SECONDS)
                    ? MonitoringEvent.Severity.CRITICAL : MonitoringEvent.Severity.WARNING;
        };
    }

    /**
     * Face checks only run when face visibility is on; camera problems are reported whenever monitoring is on. A
     * browser can never write {@code REMOVED_BY_MONITORING}: only the server does, when it removes someone.
     */
    public static boolean accepts(Effective effective, MonitoringEvent.Type type) {
        if (!effective.enabled() || !effective.logEvents()) {
            return false;
        }
        if (type == MonitoringEvent.Type.REMOVED_BY_MONITORING) {
            return false;
        }
        if (type == MonitoringEvent.Type.TAB_AWAY) {
            return effective.tabSwitchCheck();
        }
        // The microphone is only watched where an administrator made it a requirement.
        if (type == MonitoringEvent.Type.MICROPHONE_DISABLED || type == MonitoringEvent.Type.MICROPHONE_PERMISSION_DENIED) {
            return effective.microphoneRequired();
        }
        boolean faceType = type == MonitoringEvent.Type.FACE_NOT_DETECTED
                || type == MonitoringEvent.Type.FACE_RESTORED || type == MonitoringEvent.Type.MULTIPLE_FACES;
        return !faceType || effective.faceVisibility();
    }

    /**
     * Seconds to take off a student's attended time: every stretch the face was missing (counted when it came back)
     * plus every stretch the class tab was left. A face still missing when the class ended is counted up to the
     * last note, as the notes give no later moment.
     */
    public static int penaltySeconds(List<MonitoringEvent> studentEvents, Effective effective) {
        if (!effective.attendancePenalty()) {
            return 0;
        }
        long total = 0;
        MonitoringEvent openAbsence = null;
        for (MonitoringEvent e : studentEvents) {
            switch (e.getEventType()) {
                case FACE_NOT_DETECTED -> openAbsence = e;
                case FACE_RESTORED -> {
                    total += e.getDurationSeconds() == null ? 0 : e.getDurationSeconds();
                    openAbsence = null;
                }
                case TAB_AWAY -> total += e.getDurationSeconds() == null ? 0 : e.getDurationSeconds();
                default -> { }
            }
        }
        if (openAbsence != null && openAbsence.getDurationSeconds() != null) {
            total += openAbsence.getDurationSeconds();
        }
        return (int) Math.min(total, Integer.MAX_VALUE);
    }
}
