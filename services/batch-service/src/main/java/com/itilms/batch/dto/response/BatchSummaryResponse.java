package com.itilms.batch.dto.response;

import java.time.LocalDate;
import java.time.LocalTime;

import com.itilms.batch.entity.Batch;

import io.swagger.v3.oas.annotations.media.Schema;

/** A batch as it appears in a list. */
@Schema(description = "Batch listing row")
public record BatchSummaryResponse(
        Long id,
        String batchCode,
        String name,
        Long courseId,
        String courseTitle,
        Long trainerId,
        String trainerName,
        LocalDate startDate,
        LocalDate endDate,
        LocalTime startTime,
        LocalTime endTime,
        String classDays,
        String mode,
        Integer capacity,
        long enrolledCount,
        String status
) {

    public static BatchSummaryResponse from(Batch batch, long enrolledCount) {
        return new BatchSummaryResponse(
                batch.getId(), batch.getBatchCode(), batch.getName(),
                batch.getCourseId(), batch.getCourseTitle(),
                batch.getTrainerId(), batch.getTrainerName(),
                batch.getStartDate(), batch.getEndDate(),
                batch.getStartTime(), batch.getEndTime(), batch.getClassDays(),
                batch.getMode().name(), batch.getCapacity(), enrolledCount,
                batch.getStatus().name());
    }
}
