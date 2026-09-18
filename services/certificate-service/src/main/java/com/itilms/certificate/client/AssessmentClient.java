package com.itilms.certificate.client;

import java.util.List;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import lombok.extern.slf4j.Slf4j;

/** Tests passed and assignments marked (Doc S7.3). */
@FeignClient(name = "assessment-service", fallbackFactory = AssessmentClient.Fallback.class)
public interface AssessmentClient {

    @GetMapping("/api/results/students/{studentId}/completion")
    Completion completion(@PathVariable("studentId") Long studentId,
                          @RequestParam("courseId") Long courseId,
                          @RequestParam("batchId") Long batchId);

    record Completion(int testsRequired, int testsPassed, Integer averageTestPercent,
                      int assignmentsRequired, int assignmentsEvaluated,
                      boolean complete, List<String> outstanding) {
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
