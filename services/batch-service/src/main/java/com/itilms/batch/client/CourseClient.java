package com.itilms.batch.client;

import java.util.List;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import lombok.extern.slf4j.Slf4j;

/** Reads course titles and codes when a batch is created or listed. */
@FeignClient(name = "course-service", path = "/api/courses",
        fallbackFactory = CourseClient.Fallback.class)
public interface CourseClient {

    @GetMapping("/{id}")
    CourseDetail get(@PathVariable("id") Long id);

    @PostMapping("/internal/lookup")
    List<CourseSummary> lookup(@RequestBody List<Long> courseIds);

    record CourseSummary(Long id, String title, String code, String status) {
    }

    /** Only the envelope's course field is needed; the curriculum is ignored. */
    record CourseDetail(CourseSummary course) {
    }

    /**
     * Course-service being down must not stop a class running.
     *
     * <p>A batch listing without course titles is a cosmetic problem. Refusing
     * to render the timetable, so nobody knows where to be at 10am, is not. The
     * fallback returns empty data and lets the caller carry on.
     */
    @Slf4j
    @Component
    class Fallback implements FallbackFactory<CourseClient> {

        @Override
        public CourseClient create(Throwable cause) {
            return new CourseClient() {

                @Override
                public CourseDetail get(Long id) {
                    log.warn("course-service unreachable while reading course {}", id, cause);
                    return null;
                }

                @Override
                public List<CourseSummary> lookup(List<Long> courseIds) {
                    log.warn("course-service unreachable during lookup of {} course(s)", courseIds.size());
                    return List.of();
                }
            };
        }
    }
}
