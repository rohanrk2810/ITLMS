package com.itilms.liveclass.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.itilms.liveclass.entity.MonitoringEvent;
import com.itilms.liveclass.entity.MonitoringEvent.Severity;
import com.itilms.liveclass.entity.MonitoringEvent.Type;
import com.itilms.liveclass.entity.MonitoringSetting;
import com.itilms.liveclass.entity.MonitoringSetting.Scope;
import com.itilms.liveclass.service.MonitoringRules.Effective;

/** The stricter monitoring options: each is its own switch and does nothing until switched on. */
class MonitoringStrictRulesTest {

    private static Effective with(boolean tab, boolean penalty, boolean strict) {
        return Effective.of(MonitoringSetting.builder().scopeType(Scope.INSTITUTE).scopeId(0L).enabled(true)
                .faceVisibility(true).warningAfterSeconds(10).showWarning(true).logEvents(true)
                .tabSwitchCheck(tab).attendancePenalty(penalty).strictSeverity(strict).build());
    }

    private static MonitoringEvent event(Type type, Integer seconds) {
        return MonitoringEvent.builder().eventType(type).severity(Severity.WARNING).durationSeconds(seconds).build();
    }

    @Test
    void everyOptionIsOffUnlessSwitchedOn() {
        Effective e = with(false, false, false);

        assertThat(MonitoringRules.accepts(e, Type.TAB_AWAY)).isFalse();
        assertThat(MonitoringRules.penaltySeconds(List.of(event(Type.FACE_RESTORED, 40)), e)).isZero();
        assertThat(MonitoringRules.severity(Type.MULTIPLE_FACES, null, e.strictSeverity())).isEqualTo(Severity.WARNING);
    }

    @Test
    void tabSwitchNotesAreAcceptedOnlyWhenTheTabSwitchIsOn() {
        assertThat(MonitoringRules.accepts(with(true, false, false), Type.TAB_AWAY)).isTrue();
    }

    @Test
    void aBrowserCanNeverWriteARemovalNote() {
        assertThat(MonitoringRules.accepts(with(true, true, true), Type.REMOVED_BY_MONITORING)).isFalse();
    }

    @Test
    void strictSeverityMakesAMissingFaceAndSeveralFacesCriticalAtOnce() {
        assertThat(MonitoringRules.severity(Type.MULTIPLE_FACES, null, true)).isEqualTo(Severity.CRITICAL);
        assertThat(MonitoringRules.severity(Type.FACE_NOT_DETECTED, 10, true)).isEqualTo(Severity.CRITICAL);
        assertThat(MonitoringRules.severity(Type.FACE_NOT_DETECTED, 10, false)).isEqualTo(Severity.WARNING);
    }

    @Test
    void aTabLeftForLongerIsMoreSerious() {
        assertThat(MonitoringRules.severity(Type.TAB_AWAY, 20, false)).isEqualTo(Severity.WARNING);
        assertThat(MonitoringRules.severity(Type.TAB_AWAY, 40, false)).isEqualTo(Severity.WARNING);
        assertThat(MonitoringRules.severity(Type.TAB_AWAY, 40, true)).isEqualTo(Severity.CRITICAL);
        assertThat(MonitoringRules.severity(Type.TAB_AWAY, 90, false)).isEqualTo(Severity.CRITICAL);
    }

    @Test
    void thePenaltyAddsEveryAbsenceAndEveryTabAwayStretch() {
        List<MonitoringEvent> events = List.of(
                event(Type.FACE_NOT_DETECTED, 10), event(Type.FACE_NOT_DETECTED, 60), event(Type.FACE_RESTORED, 75),
                event(Type.TAB_AWAY, 30),
                event(Type.FACE_NOT_DETECTED, 12), event(Type.FACE_RESTORED, 20));

        assertThat(MonitoringRules.penaltySeconds(events, with(true, true, false))).isEqualTo(75 + 30 + 20);
    }

    @Test
    void anAbsenceThatNeverEndedIsStillCounted() {
        List<MonitoringEvent> events = List.of(event(Type.FACE_NOT_DETECTED, 10), event(Type.FACE_NOT_DETECTED, 60));

        assertThat(MonitoringRules.penaltySeconds(events, with(false, true, false))).isEqualTo(60);
    }
}
