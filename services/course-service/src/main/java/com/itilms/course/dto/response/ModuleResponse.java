package com.itilms.course.dto.response;

import java.util.List;

import com.itilms.course.entity.CourseModule;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Course module with its lessons")
public record ModuleResponse(
        Long id,
        Long courseId,
        String title,
        String description,
        Integer sequenceNo,
        List<LessonResponse> lessons
) {

    public static ModuleResponse from(CourseModule module, List<LessonResponse> lessons) {
        return new ModuleResponse(
                module.getId(), module.getCourseId(), module.getTitle(),
                module.getDescription(), module.getSequenceNo(),
                lessons == null ? List.of() : lessons);
    }
}
