package com.itilms.batch.dto.response;

import java.time.LocalDate;
import java.time.LocalTime;

import com.itilms.batch.entity.ClassSession;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A scheduled lecture")
public record SessionResponse(
        Long id,
        Long batchId,
        String batchCode,
        String courseTitle,
        Long trainerId,
        String trainerName,
        LocalDate sessionDate,
        LocalTime startTime,
        LocalTime endTime,
        String topic,
        String mode,
        String meetingUrl,
        String room,
        String status,
        boolean attendanceMarked,
        @Schema(description = "True when the register came from live-room activity rather than a trainer")
        boolean attendanceAuto,
        String cancelledReason,
        @Schema(description = "True when this session is an online or hybrid class with a live room")
        boolean live
) {

    public static SessionResponse from(ClassSession session, String batchCode,
                                       String courseTitle, String trainerName) {
        return new SessionResponse(
                session.getId(), session.getBatchId(), batchCode, courseTitle,
                session.getTrainerId(), trainerName,
                session.getSessionDate(), session.getStartTime(), session.getEndTime(),
                session.getTopic(), session.getMode().name(), session.getMeetingUrl(),
                session.getRoom(), session.getStatus().name(),
                session.isAttendanceMarked(), session.isAttendanceAuto(),
                session.getCancelledReason(),
                session.getMode().needsLiveRoom());
    }

    public static SessionResponse from(ClassSession session) {
        return from(session, null, null, null);
    }
}
