package com.itilms.batch.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A batch as the public website shows it: what it covers, when it meets, what it costs, and whether a seat is left.
 * Nothing about who is in it.
 */
@Schema(description = "A batch offered for sale on the public website")
public record PublicBatchResponse(
        Long id,
        String batchCode,
        String name,
        @Schema(description = "The course the batch was created for")
        Long courseId,
        @Schema(description = "Every course the batch holds, by title")
        List<String> courseTitles,
        LocalDate startDate,
        LocalDate endDate,
        LocalTime startTime,
        LocalTime endTime,
        List<String> classDays,
        String mode,
        long seatsLeft,
        @Schema(description = "What the whole batch costs")
        BigDecimal fee
) {
}
