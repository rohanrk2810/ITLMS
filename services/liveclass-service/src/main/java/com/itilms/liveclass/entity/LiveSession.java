package com.itilms.liveclass.entity;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

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

/** One online class: a LiveKit room standing in for a scheduled lecture. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "live_sessions")
public class LiveSession extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "class_session_id", nullable = false)
    private Long classSessionId;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "batch_code", length = 40)
    private String batchCode;

    @Column(name = "course_title", length = 200)
    private String courseTitle;

    @Column(name = "trainer_id")
    private Long trainerId;

    @Column(name = "trainer_user_id")
    private Long trainerUserId;

    @Column(name = "room_name", nullable = false, length = 120)
    private String roomName;

    @Column(name = "livekit_room_sid", length = 80)
    private String livekitRoomSid;

    @Column(length = 255)
    private String topic;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "scheduled_start_at", nullable = false)
    private Instant scheduledStartAt;

    @Column(name = "scheduled_end_at", nullable = false)
    private Instant scheduledEndAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private LiveSessionStatus status = LiveSessionStatus.SCHEDULED;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "max_participants", nullable = false)
    @Builder.Default
    private int maxParticipants = 100;

    @Column(name = "peak_participants", nullable = false)
    @Builder.Default
    private int peakParticipants = 0;

    @Column(name = "recording_enabled", nullable = false)
    @Builder.Default
    private boolean recordingEnabled = false;

    @Column(name = "recording_url", length = 600)
    private String recordingUrl;

    @Column(name = "attendance_computed", nullable = false)
    @Builder.Default
    private boolean attendanceComputed = false;

    @Column(name = "attendance_computed_at")
    private Instant attendanceComputedAt;

    /**
     * The window during which a token will be minted for this room.
     *
     * <p>Outside it, a student asking to join is refused. Without this, a join
     * token for last Tuesday's class would still open a room today, and the
     * room would fill up with people the trainer is not expecting.
     */
    public boolean joinWindowOpen(Instant now, int earlyMinutes, int graceMinutes) {
        return !now.isBefore(scheduledStartAt.minus(Duration.ofMinutes(earlyMinutes)))
                && !now.isAfter(scheduledEndAt.plus(Duration.ofMinutes(graceMinutes)));
    }

    /**
     * The length of class a student's time is measured against, in seconds.
     *
     * <p>It runs from when teaching could begin to when it stopped, bounded on
     * both sides by the timetable:
     * <ul>
     *   <li>It starts at the scheduled start or when the host arrived, whichever
     *       is later. A trainer ten minutes late does not cost every student ten
     *       minutes; a room pre-created half an hour early does not stretch the
     *       class by half an hour and push a fully present student down to LATE.</li>
     *   <li>It ends at the scheduled end or when the class was closed, whichever
     *       is earlier. A class ended early is judged on what was taught; an
     *       overrun is a bonus nobody is marked down for leaving.</li>
     * </ul>
     *
     * <p>Student stints are clipped to the same scheduled bounds when they are
     * recorded (see {@link LiveParticipant#left}), so both sides of the
     * percentage are measured on one footing. When the window comes out empty -
     * the host arrived after the scheduled end - the scheduled length stands in
     * rather than dividing by zero.
     */
    public int attendanceWindowSeconds(Instant hostArrivedAt) {
        Instant from = hostArrivedAt != null && hostArrivedAt.isAfter(scheduledStartAt)
                ? hostArrivedAt : scheduledStartAt;
        Instant to = endedAt != null && endedAt.isBefore(scheduledEndAt)
                ? endedAt : scheduledEndAt;

        long seconds = Duration.between(from, to).toSeconds();
        return seconds > 0 ? (int) seconds : scheduledDurationSeconds();
    }

    public int scheduledDurationSeconds() {
        return (int) Duration.between(scheduledStartAt, scheduledEndAt).toSeconds();
    }

    /**
     * Records the first person entering.
     *
     * <p>Driven by the first participant joining, not by LiveKit's room-started
     * notice: rooms are created ahead of time, and a class shown as "live" half an
     * hour before anyone is in it sends students into an empty room.
     */
    public void markLive(Instant at) {
        if (startedAt == null) {
            startedAt = at;
        }
        if (status == LiveSessionStatus.SCHEDULED) {
            status = LiveSessionStatus.LIVE;
        }
    }

    public void markEnded(Instant at) {
        if (endedAt == null) {
            endedAt = at;
        }
        if (status != LiveSessionStatus.CANCELLED) {
            status = LiveSessionStatus.ENDED;
        }
    }

    public void notePeak(int current) {
        if (current > peakParticipants) {
            peakParticipants = current;
        }
    }
}
