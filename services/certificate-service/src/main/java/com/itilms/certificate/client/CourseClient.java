package com.itilms.certificate.client;

import java.math.BigDecimal;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import lombok.extern.slf4j.Slf4j;

/** Lesson progress (Doc S7.3 "all required lessons complete") and the course's title. */
@FeignClient(name = "course-service", fallbackFactory = CourseClient.Fallback.class)
public interface CourseClient {

    @GetMapping("/api/courses/{id}")
    CourseDetail course(@PathVariable("id") Long id);

    @GetMapping("/api/progress/students/{studentId}/courses/{courseId}")
    Progress progress(@PathVariable("studentId") Long studentId, @PathVariable("courseId") Long courseId);

    record CourseDetail(CourseSummary course) {
    }

    record CourseSummary(Long id, String title, String code) {
    }

    /**
     * @param batchId the batch the student studied the course in, from their own
     *                enrolment - which is why certificate requests never take a
     *                batch id from the caller
     */
    record Progress(Long studentId, Long courseId, Long batchId, BigDecimal progressPercent,
                    int completedLessons, int totalLessons, boolean allLessonsComplete) {
    }

    /** Null answers: the checker reports the criterion as unconfirmed, and does not issue. */
    @Slf4j
    @Component
    class Fallback implements FallbackFactory<CourseClient> {

        @Override
        public CourseClient create(Throwable cause) {
            return new CourseClient() {
                @Override
                public CourseDetail course(Long id) {
                    log.warn("course-service unreachable reading course {}", id, cause);
                    return null;
                }

                @Override
                public Progress progress(Long studentId, Long courseId) {
                    log.warn("course-service unreachable reading progress of student {}", studentId, cause);
                    return null;
                }
            };
        }
    }
}
