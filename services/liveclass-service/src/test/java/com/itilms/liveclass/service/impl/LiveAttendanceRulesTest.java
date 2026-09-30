package com.itilms.liveclass.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.itilms.liveclass.config.LiveClassProperties;
import com.itilms.liveclass.entity.LiveParticipant;
import com.itilms.liveclass.entity.LiveSession;
import com.itilms.liveclass.entity.ParticipantRole;

/**
 * The arithmetic behind automatic attendance.
 *
 * <p>These rules decide whether a student's register says PRESENT or ABSENT, and
 * so whether they qualify for a certificate. Each case below is a situation a
 * trainer or student has a reason to dispute.
 */
class LiveAttendanceRulesTest {

    /** A 10:00-11:00 class. */
    private static final Instant START = Instant.parse("2026-01-12T04:30:00Z");
    private static final Instant END = START.plus(Duration.ofHours(1));

    private static Instant at(int minutesFromStart) {
        return START.plus(Duration.ofMinutes(minutesFromStart));
    }

    private static LiveSession oneHourClass() {
        return LiveSession.builder().scheduledStartAt(START).scheduledEndAt(END).build();
    }

    private static LiveParticipant student() {
        return LiveParticipant.builder().identity("user-7").userId(7L).studentId(70L)
                .role(ParticipantRole.STUDENT).build();
    }

    @Nested
    @DisplayName("Class length used as the denominator")
    class Window {

        @Test
        @DisplayName("A room pre-created early does not stretch the class")
        void earlyRoomDoesNotCount() {
            LiveSession session = oneHourClass();
            session.setEndedAt(END);

            assertThat(session.attendanceWindowSeconds(at(-30))).isEqualTo(3600);
        }

        @Test
        @DisplayName("A trainer arriving late shortens the class for everyone")
        void lateTrainerShortensWindow() {
            LiveSession session = oneHourClass();
            session.setEndedAt(END);

            assertThat(session.attendanceWindowSeconds(at(10))).isEqualTo(50 * 60);
        }

        @Test
        @DisplayName("A class ended early is judged on what was taught")
        void endedEarly() {
            LiveSession session = oneHourClass();
            session.setEndedAt(at(40));

            assertThat(session.attendanceWindowSeconds(START)).isEqualTo(40 * 60);
        }

        @Test
        @DisplayName("An overrun is not added to the class length")
        void overrunIgnored() {
            LiveSession session = oneHourClass();
            session.setEndedAt(at(80));

            assertThat(session.attendanceWindowSeconds(START)).isEqualTo(3600);
        }

        @Test
        @DisplayName("A host arriving after the scheduled end falls back to the scheduled length")
        void emptyWindowFallsBack() {
            LiveSession session = oneHourClass();
            session.setEndedAt(at(90));

            assertThat(session.attendanceWindowSeconds(at(70))).isEqualTo(3600);
        }
    }

    @Nested
    @DisplayName("A student's time in the room")
    class Stints {

        @Test
        @DisplayName("Reconnects add up rather than resetting")
        void reconnectsAccumulate() {
            LiveParticipant p = student();
            p.joined(at(0));
            p.left(at(20), START, END);
            p.joined(at(25));
            p.left(at(60), START, END);

            assertThat(p.getAttendedSeconds()).isEqualTo(55 * 60);
            assertThat(p.getJoinCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("Waiting before class and staying after it do not count")
        void clippedToSlot() {
            LiveParticipant p = student();
            p.joined(at(-15));
            p.left(at(75), START, END);

            assertThat(p.getAttendedSeconds()).isEqualTo(3600);
        }

        @Test
        @DisplayName("A redelivered join does not discard the minutes since the real join")
        void duplicateJoinIgnored() {
            LiveParticipant p = student();
            p.joined(at(0));
            p.joined(at(30));
            p.left(at(60), START, END);

            assertThat(p.getAttendedSeconds()).isEqualTo(3600);
            assertThat(p.getJoinCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("A leave with no join, or out of order, never subtracts time")
        void neverNegative() {
            LiveParticipant p = student();
            p.left(at(30), START, END);
            p.joined(at(40));
            p.left(at(35), START, END);

            assertThat(p.getAttendedSeconds()).isZero();
        }
    }

    @Nested
    @DisplayName("When an unattended class is taken to have ended")
    class CloseTime {

        @Test
        @DisplayName("A room that emptied early ended when the last person left")
        void lastLeave() {
            LiveParticipant trainer = student();
            trainer.joined(at(0));
            trainer.left(at(40), START, END);
            LiveParticipant other = student();
            other.joined(at(1));
            other.left(at(38), START, END);

            assertThat(LiveAttendanceServiceImpl.inferCloseTime(oneHourClass(), java.util.List.of(trainer, other)))
                    .isEqualTo(at(40));
        }

        @Test
        @DisplayName("Someone whose leave notice was lost is assumed to stay only to the scheduled end")
        void lostLeaveCappedAtScheduledEnd() {
            LiveParticipant stuck = student();
            stuck.joined(at(5));

            assertThat(LiveAttendanceServiceImpl.inferCloseTime(oneHourClass(), java.util.List.of(stuck)))
                    .isEqualTo(END);
        }
    }

    @Nested
    @DisplayName("Percentage and verdict")
    class Verdict {

        private final LiveAttendanceServiceImpl rules = rulesWith(70, 40);

        @Test
        @DisplayName("69.9% is not PRESENT against a 70% threshold")
        void roundsDown() {
            int percent = LiveAttendanceServiceImpl.percentOf(2517, 3600);

            assertThat(percent).isEqualTo(69);
            assertThat(rules.verdict(percent)).isEqualTo("LATE");
        }

        @Test
        @DisplayName("Thresholds are inclusive")
        void inclusiveThresholds() {
            assertThat(rules.verdict(70)).isEqualTo("PRESENT");
            assertThat(rules.verdict(40)).isEqualTo("LATE");
            assertThat(rules.verdict(39)).isEqualTo("ABSENT");
        }

        @Test
        @DisplayName("Time beyond a shortened class is capped at 100%")
        void cappedAtHundred() {
            assertThat(LiveAttendanceServiceImpl.percentOf(3600, 50 * 60)).isEqualTo(100);
        }

        @Test
        @DisplayName("No time, or an unusable class length, is 0% rather than an error")
        void zeroCases() {
            assertThat(LiveAttendanceServiceImpl.percentOf(0, 3600)).isZero();
            assertThat(LiveAttendanceServiceImpl.percentOf(600, 0)).isZero();
        }

        private static LiveAttendanceServiceImpl rulesWith(int present, int late) {
            LiveClassProperties props = new LiveClassProperties();
            props.setPresentThresholdPercent(present);
            props.setLateThresholdPercent(late);
            return new LiveAttendanceServiceImpl(null, null, null, props, null);
        }
    }
}
