package com.itilms.placement.client;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import lombok.extern.slf4j.Slf4j;

/** The student's average test result in a course, for a job's minimum-score rule. */
@FeignClient(name = "assessment-service", fallbackFactory = AssessmentClient.Fallback.class)
public interface AssessmentClient {

    @GetMapping("/api/results/students/{studentId}/completion")
    Completion completion(@PathVariable("studentId") Long studentId,
                          @RequestParam("courseId") Long courseId,
                          @RequestParam("batchId") Long batchId);

    record Completion(int testsRequired, Integer averageTestPercent) {
    }

    @Slf4j
    @Component
    class Fallback implements FallbackFactory<AssessmentClient> {

        @Override
        public AssessmentClient create(Throwable cause) {
            return (studentId, courseId, batchId) -> {
                log.warn("assessment-service unreachable reading results of student {}", studentId, cause);
                return null;
            };
        }
    }
}
