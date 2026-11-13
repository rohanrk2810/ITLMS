package com.itilms.batch.dto.response;

import java.time.Instant;
import java.time.LocalDate;

import com.itilms.batch.entity.Enrollment;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A student's place in a batch")
public record EnrollmentResponse(
        Long id,
        Long studentId,
        Long userId,
        String studentCode,
        String studentName,
        Long courseId,
        Long batchId,
        Instant enrolledAt,
        String status,
        LocalDate completionDate,
        String droppedReason,
        LocalDate accessUntil,
        @Schema(description = "Placed in this one course alone (and pays its own fee), not in the whole batch")
        boolean courseOnly
) {

    public static EnrollmentResponse from(Enrollment enrollment) {
        return new EnrollmentResponse(
                enrollment.getId(), enrollment.getStudentId(), enrollment.getUserId(),
                enrollment.getStudentCode(), enrollment.getStudentName(),
                enrollment.getCourseId(), enrollment.getBatchId(),
                enrollment.getEnrolledAt(), enrollment.getStatus().name(),
                enrollment.getCompletionDate(), enrollment.getDroppedReason(), enrollment.getAccessUntil(),
                enrollment.isCourseOnly());
    }
}
