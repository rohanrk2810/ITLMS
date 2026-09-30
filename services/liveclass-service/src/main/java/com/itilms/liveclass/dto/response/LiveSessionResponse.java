package com.itilms.liveclass.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.itilms.liveclass.entity.LiveSession;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "An online class and its room")
public record LiveSessionResponse(
        Long id,
        Long classSessionId,
        Long batchId,
        String batchCode,
        String courseTitle,
        Long trainerId,
        String topic,
        String roomName,
        LocalDate sessionDate,
        LocalTime startTime,
        LocalTime endTime,
        Instant scheduledStartAt,
        Instant scheduledEndAt,
        String status,
        Instant startedAt,
        Instant endedAt,
        int peakParticipants,
        boolean recordingEnabled,
        String recordingUrl,

        @Schema(description = "A capture is running right now")
        boolean recording,

        boolean attendanceComputed,

        @Schema(description = "True when the room will accept a join request at this moment")
        boolean joinable,

        @Schema(description = "Present only on the single-session view")
        List<LiveParticipantResponse> participants
) {

    public static LiveSessionResponse summary(LiveSession s, boolean joinable) {
        return build(s, joinable, null);
    }

    public static LiveSessionResponse detail(LiveSession s, boolean joinable,
                                             List<LiveParticipantResponse> participants) {
        return build(s, joinable, participants);
    }

    private static LiveSessionResponse build(LiveSession s, boolean joinable,
                                             List<LiveParticipantResponse> participants) {
        return new LiveSessionResponse(
                s.getId(), s.getClassSessionId(), s.getBatchId(), s.getBatchCode(),
                s.getCourseTitle(), s.getTrainerId(), s.getTopic(), s.getRoomName(),
                s.getSessionDate(), s.getStartTime(), s.getEndTime(),
                s.getScheduledStartAt(), s.getScheduledEndAt(),
                s.getStatus().name(), s.getStartedAt(), s.getEndedAt(),
                s.getPeakParticipants(), s.isRecordingEnabled(), s.getRecordingUrl(), s.getEgressId() != null,
                s.isAttendanceComputed(), joinable, participants);
    }
}
