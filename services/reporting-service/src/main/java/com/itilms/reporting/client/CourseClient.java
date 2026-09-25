package com.itilms.reporting.client;

import java.util.List;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import lombok.extern.slf4j.Slf4j;

/**
 * A student's progress through each course, module by module.
 *
 * <p>Every client here fails soft: when the service is down it answers null and the progress report says that
 * section is unavailable, instead of refusing to show the rest.
 */
@FeignClient(name = "course-service", fallbackFactory = CourseClient.Fallback.class)
public interface CourseClient {

    @GetMapping("/api/progress/internal/students/{studentId}")
    List<CourseProgress> progressOf(@PathVariable("studentId") Long studentId);

    record CourseProgress(Long courseId, String courseTitle, Long batchId, String status, int progressPercent,
                          int completedLessons, int totalLessons, int videoLessons, int videoLessonsCompleted,
                          List<ModuleProgress> modules) {
    }

    record ModuleProgress(Long moduleId, String title, int lessons, int completed) {

        public boolean isComplete() {
            return completed >= lessons;
        }
    }

    @Slf4j
    @Component
    class Fallback implements FallbackFactory<CourseClient> {

        @Override
        public CourseClient create(Throwable cause) {
            return studentId -> {
                log.warn("course-service unavailable for a progress report: {}", cause.toString());
                return null;
            };
        }
    }
}