package com.itilms.liveclass.entity;

import java.time.Duration;
import java.time.Instant;

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

/**
 * One person's time in one live class, totalled across every reconnect.
 *
 * <p>The row is created when the join token is minted, not when the webhook
 * arrives. That ordering matters: it means the service already knows who
 * {@code user-42} is by the time LiveKit reports them joining, so the webhook
 * handler never has to trust a client-supplied name or guess at a student id.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "live_participants")
public class LiveParticipant extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "live_session_id", nullable = false)
    private Long liveSessionId;

    @Column(nullable = false, length = 80)
    private String identity;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "student_id")
    private Long studentId;

    @Column(name = "display_name", length = 160)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ParticipantRole role;

    @Column(name = "first_joined_at")
    private Instant firstJoinedAt;

    @Column(name = "last_left_at")
    private Instant lastLeftAt;

    @Column(name = "current_join_at")
    private Instant currentJoinAt;

    @Column(name = "attended_seconds", nullable = false)
    @Builder.Default
    private int attendedSeconds = 0;

    @Column(name = "join_count", nullable = false)
    @Builder.Default
    private int joinCount = 0;

    @Column(name = "attendance_percent")
    private Integer attendancePercent;

    @Column(name = "computed_status", length = 20)
    private String computedStatus;

    /** A host's override of the room policy for this one person. Null means follow the room. */
    @Column(name = "mic_allowed")
    private Boolean micAllowed;

    @Column(name = "camera_allowed")
    private Boolean cameraAllowed;

    @Column(name = "screen_allowed")
    private Boolean screenAllowed;

    public boolean inRoom() {
        return currentJoinAt != null;
    }

    /**
     * Opens a stint.
     *
     * <p>A repeated join event without an intervening leave is ignored rather
     * than treated as a new stint: LiveKit can redeliver, and overwriting the
     * open timestamp would quietly discard the minutes since the real join.
     */
    public void joined(Instant at) {
        if (currentJoinAt != null) {
            return;
        }
        currentJoinAt = at;
        joinCount++;
        if (firstJoinedAt == null) {
            firstJoinedAt = at;
        }
    }

    /**
     * Closes a stint and adds the part of it that fell inside class time.
     *
     * <p>Only the overlap with {@code [countFrom, countUntil]} - the scheduled
     * slot - counts. Twenty minutes chatting in the room before class does not
     * make up for leaving twenty minutes early, and staying on after the end
     * does not pad a total.
     *
     * <p>A leave with no matching join adds nothing. Negative spans - clock skew
     * between LiveKit and this service, or an out-of-order delivery - count as
     * zero rather than being subtracted, so a total can never go down.
     */
    public void left(Instant at, Instant countFrom, Instant countUntil) {
        if (currentJoinAt == null) {
            return;
        }
        Instant from = currentJoinAt.isAfter(countFrom) ? currentJoinAt : countFrom;
        Instant to = at.isBefore(countUntil) ? at : countUntil;

        long seconds = Duration.between(from, to).toSeconds();
        if (seconds > 0) {
            attendedSeconds += (int) seconds;
        }
        lastLeftAt = at;
        currentJoinAt = null;
    }

    public void applyComputed(int percent, String status) {
        this.attendancePercent = percent;
        this.computedStatus = status;
    }
}
