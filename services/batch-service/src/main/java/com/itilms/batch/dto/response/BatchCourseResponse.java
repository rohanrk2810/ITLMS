package com.itilms.batch.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** One course a batch holds, with the trainer who teaches it there. */
@Schema(description = "A course in a batch")
public record BatchCourseResponse(
        Long courseId,
        String courseCode,
        String courseTitle,
        Long trainerId,
        String trainerName,
        @Schema(description = "True for the course the batch was created for; it cannot be taken out")
        boolean main
) {
}
