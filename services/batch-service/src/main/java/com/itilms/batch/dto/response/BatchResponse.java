package com.itilms.batch.dto.response;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.itilms.batch.entity.Batch;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Batch")
public record BatchResponse(
        Long id,
        String batchCode,
        String name,
        Long courseId,
        String courseCode,
        String courseTitle,
        Long trainerId,
        String trainerName,
        LocalDate startDate,
        LocalDate endDate,
        LocalTime startTime,
        LocalTime endTime,
        List<String> classDays,
        String mode,
        Integer capacity,
        @Schema(description = "Seats currently taken by active enrolments")
        long enrolledCount,
        @Schema(description = "capacity - enrolledCount, never negative")
        long seatsAvailable,
        String classroom,
        String meetingUrl,
        String status,
        List<CoTrainerResponse> coTrainers
) {

    public static BatchResponse from(Batch batch, long enrolledCount, List<CoTrainerResponse> coTrainers) {
        return new BatchResponse(
                batch.getId(), batch.getBatchCode(), batch.getName(),
                batch.getCourseId(), batch.getCourseCode(), batch.getCourseTitle(),
                batch.getTrainerId(), batch.getTrainerName(),
                batch.getStartDate(), batch.getEndDate(),
                batch.getStartTime(), batch.getEndTime(),
                List.of(batch.getClassDays().split(",")),
                batch.getMode().name(), batch.getCapacity(), enrolledCount,
                Math.max(0, batch.getCapacity() - enrolledCount),
                batch.getClassroom(), batch.getMeetingUrl(), batch.getStatus().name(),
                coTrainers == null ? List.of() : coTrainers);
    }

    @Schema(description = "An additional trainer on this batch")
    public record CoTrainerResponse(Long trainerId, String trainerName, String role) {
    }
}
