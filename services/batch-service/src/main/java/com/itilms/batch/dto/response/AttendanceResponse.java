package com.itilms.batch.dto.response;

import java.time.Instant;
import java.time.LocalDate;

import com.itilms.batch.entity.Attendance;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "One attendance record")
public record AttendanceResponse(
        Long id,
        Long sessionId,
        Long studentId,
        String studentName,
        String status,
        String remark,
        String source,
        Integer attendedMinutes,
        Instant markedAt,
        Long markedBy,
        @Schema(description = "Set when a trainer or coordinator overrode the original mark")
        Instant correctedAt,
        Long correctedBy,
        LocalDate sessionDate,
        String topic
) {

    public static AttendanceResponse from(Attendance attendance, String studentName,
                                          LocalDate sessionDate, String topic) {
        return new AttendanceResponse(
                attendance.getId(), attendance.getSessionId(), attendance.getStudentId(), studentName,
                attendance.getStatus().name(), attendance.getRemark(), attendance.getSource().name(),
                attendance.getAttendedMinutes(), attendance.getMarkedAt(), attendance.getMarkedBy(),
                attendance.getCorrectedAt(), attendance.getCorrectedBy(), sessionDate, topic);
    }
}
