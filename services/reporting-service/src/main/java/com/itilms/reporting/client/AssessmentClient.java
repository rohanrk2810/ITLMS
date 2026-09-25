package com.itilms.reporting.client;

import java.time.Instant;
import java.util.List;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import lombok.extern.slf4j.Slf4j;

/** A student's results on tests, coding questions and assignments. */
@FeignClient(name = "assessment-service", fallbackFactory = AssessmentClient.Fallback.class)
public interface AssessmentClient {

    /** {@code forStudent} holds back the results a trainer has not released, as the student's own results page does. */
    @GetMapping("/api/results/internal/students/{studentId}/performance")
    Performance performance(@PathVariable("studentId") Long studentId,
                            @RequestParam("batchIds") List<Long> batchIds,
                            @RequestParam("courseIds") List<Long> courseIds,
                            @RequestParam("forStudent") boolean forStudent);

    record Performance(Tests tests, Coding coding, Assignments assignments) {
    }

    record Pending(Long id, String title, Instant dueAt, boolean overdue) {
    }

    record Weak(Long id, String title, int percent, int passPercent) {
    }

    record Tests(int attempted, int passed, Integer averagePercent, Integer bestPercent, int terminated,
                 int resultsPending, List<Pending> pending, List<Weak> below) {
    }

    record CodingWeak(Long questionId, String question, int passed, int total) {
    }

    record Coding(int questionsAttempted, int testCasesPassed, int testCasesTotal, Integer averagePercent,
                  List<CodingWeak> lowest) {
    }

    record Assignments(int assigned, int submitted, int evaluated, Integer averagePercent, int returned, int late,
                       List<Pending> pending) {
    }

    @Slf4j
    @Component
    class Fallback implements FallbackFactory<AssessmentClient> {

        @Override
        public AssessmentClient create(Throwable cause) {
            return (studentId, batchIds, courseIds, forStudent) -> {
                log.warn("assessment-service unavailable for a progress report: {}", cause.toString());
                return null;
            };
        }
    }
}