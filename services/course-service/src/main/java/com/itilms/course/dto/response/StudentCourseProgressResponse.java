package com.itilms.course.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One course as one student is getting on with it: the overall percentage, which modules are still unfinished,
 * and how many of the recorded (video) lessons they have watched to the end. For the student's progress report.
 */
@Schema(description = "A student's progress through one course, module by module")
public record StudentCourseProgressResponse(
        Long courseId,
        String courseTitle,
        Long batchId,
        String status,
        @Schema(description = "Mandatory lessons finished, as a percentage")
        int progressPercent,
        int completedLessons,
        @Schema(description = "Mandatory lessons in the course")
        int totalLessons,
        @Schema(description = "Video lessons in the course, mandatory or not")
        int videoLessons,
        int videoLessonsCompleted,
        List<ModuleProgress> modules
) {

    @Schema(description = "Mandatory lessons in one module and how many are finished")
    public record ModuleProgress(Long moduleId, String title, int lessons, int completed) {

        public boolean isComplete() {
            return completed >= lessons;
        }
    }
}
