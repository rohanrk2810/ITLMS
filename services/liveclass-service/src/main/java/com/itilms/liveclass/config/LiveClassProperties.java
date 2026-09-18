package com.itilms.liveclass.config;

import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * The institute's rules for running an online class.
 *
 * <p>These are settings rather than constants because institutes disagree about
 * them. A three-hour weekend batch and a one-hour evening batch cannot share a
 * fixed "present means 45 minutes" rule, which is why the thresholds are
 * percentages of the session rather than minute counts.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "itilms.live-class")
public class LiveClassProperties {

    /** At or above this share of the session, a student is PRESENT. */
    private int presentThresholdPercent = 70;

    /** At or above this share but below present, a student is LATE. */
    private int lateThresholdPercent = 40;

    /**
     * How long before the scheduled start a student may enter.
     *
     * <p>Early entry is deliberate: people sort out microphones before the
     * trainer arrives rather than during the first ten minutes of teaching.
     */
    private int earlyJoinMinutes = 15;

    /**
     * How long after the scheduled end the room stays reachable.
     *
     * <p>Classes overrun. Cutting the room off at the minute would end a
     * lecture mid-sentence.
     */
    private int lateJoinGraceMinutes = 30;

    /**
     * How long LiveKit keeps a room open after the last person leaves.
     *
     * <p>A trainer whose wifi drops rejoins the same room and the class carries
     * on, instead of the room closing behind them. LiveKit's own default is 20
     * seconds, which is shorter than most router restarts.
     */
    private int departureTimeoutSeconds = 300;

    /**
     * How close to the scheduled end a room must close to count as the class
     * finishing.
     *
     * <p>A room that empties at 10:25 in a 10:00-11:00 class is a dropped
     * connection, not the end of the lecture: settling attendance then would
     * lock everyone out for the remaining half hour. A room that empties at
     * 10:50 is a class that finished a little early.
     */
    private int finishMarginMinutes = 15;

    private int maxParticipants = 100;

    private boolean recordingEnabled = false;

    /**
     * How long after the scheduled end the sweep waits before settling a
     * session itself.
     *
     * <p>LiveKit normally reports the room finishing and attendance is computed
     * from that. This covers the case where it never does - the last webhook is
     * lost, or nobody ever opened the room - so a class cannot sit unsettled
     * forever and quietly leave a register blank.
     */
    private int settleAfterEndMinutes = 45;

    /** How far ahead the pre-provisioning job creates rooms. */
    private int preProvisionMinutes = 30;

    /**
     * The timezone the institute schedules in.
     *
     * <p>Session times are stored as wall-clock times by batch-service, so
     * turning "Monday 10:00" into an instant needs a zone. Taking it from
     * configuration rather than the server's default means a container running
     * in UTC does not silently shift every class by five and a half hours.
     */
    private ZoneId zone = ZoneId.of("Asia/Kolkata");
}
