package com.itilms.finance.client;

import java.math.BigDecimal;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** The price and state of a course, read from the catalog that owns them. */
@FeignClient(name = "course-service", path = "/api/courses", fallbackFactory = CourseClient.Fallback.class)
public interface CourseClient {

    @GetMapping("/{id}")
    CourseDetail get(@PathVariable("id") Long id);

    /** The same, without a sign-in: for a consumer of Kafka events, which has none to pass on. */
    @GetMapping("/public/{id}")
    CourseDetail publicDetail(@PathVariable("id") Long id);

    record CourseDetail(Course course) {
    }

    record Course(Long id, String title, String status, BigDecimal fee) {
    }

    /** No answer, no sale: a price that cannot be confirmed is never charged. */
    @Component
    class Fallback implements FallbackFactory<CourseClient> {
        @Override
        public CourseClient create(Throwable cause) {
            return new CourseClient() {
                @Override
                public CourseDetail get(Long id) {
                    return null;
                }

                @Override
                public CourseDetail publicDetail(Long id) {
                    return null;
                }
            };
        }
    }
}
