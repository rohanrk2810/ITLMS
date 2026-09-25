package com.itilms.batch.dto.request;

import java.time.Instant;

import com.itilms.batch.entity.CourseRequest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request and response bodies for course access requests. */
public final class CourseRequestDtos {

    private CourseRequestDtos() {
    }

    public record CreateRequest(
            @NotNull Long courseId,
            @Schema(description = "A batch of that course the student would like to join (optional)") Long batchId,
            @Size(max = 1000) String message) {
    }

    @Schema(description = "Approving needs a batch: the one the student asked for, or another batch of the same course")
    public record ApproveRequest(
            Long batchId,
            @Size(max = 500) String note) {
    }

    public record RejectRequest(
            @NotBlank @Size(max = 500) String note) {
    }

    public record CourseRequestResponse(
            Long id, String status,
            Long studentId, String studentCode, String studentName, String studentEmail, String studentPhone,
            Long courseId, String courseCode, String courseTitle,
            Long preferredBatchId, String message,
            Long decidedBy, String decidedByName, Instant decidedAt, String decisionNote,
            Long approvedBatchId, Long enrollmentId,
            Instant createdAt) {

        public static CourseRequestResponse from(CourseRequest r) {
            return new CourseRequestResponse(r.getId(), r.getStatus().name(),
                    r.getStudentId(), r.getStudentCode(), r.getStudentName(), r.getStudentEmail(), r.getStudentPhone(),
                    r.getCourseId(), r.getCourseCode(), r.getCourseTitle(),
                    r.getPreferredBatchId(), r.getMessage(),
                    r.getDecidedBy(), r.getDecidedByName(), r.getDecidedAt(), r.getDecisionNote(),
                    r.getApprovedBatchId(), r.getEnrollmentId(),
                    r.getCreatedAt());
        }
    }
}
