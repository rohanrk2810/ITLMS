package com.itilms.batch.dto.response;

import java.time.LocalDate;

import com.itilms.batch.entity.Batch;
import com.itilms.batch.entity.Enrollment;

import io.swagger.v3.oas.annotations.media.Schema;

/** One place a student holds (or held) in one batch, with the batch's own details, for their progress report. */
@Schema(description = "A student's enrolment in a batch")
public record StudentEnrollmentResponse(
        Long enrollmentId,
        String status,
        Long batchId,
        String batchCode,
        String batchName,
        String batchStatus,
        Long courseId,
        String courseTitle,
        String trainerName,
        String mode,
        LocalDate startDate,
        LocalDate endDate
) {

    public static StudentEnrollmentResponse of(Enrollment enrollment, Batch batch) {
        return new StudentEnrollmentResponse(enrollment.getId(), enrollment.getStatus().name(),
                enrollment.getBatchId(),
                batch == null ? null : batch.getBatchCode(),
                batch == null ? null : batch.getName(),
                batch == null ? null : batch.getStatus().name(),
                enrollment.getCourseId(),
                batch == null ? null : batch.getCourseTitle(),
                batch == null ? null : batch.getTrainerName(),
                batch == null ? null : batch.getMode().name(),
                batch == null ? null : batch.getStartDate(),
                batch == null ? null : batch.getEndDate());
    }
}
