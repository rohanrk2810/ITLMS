package com.itilms.placement.client;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import lombok.extern.slf4j.Slf4j;

/**
 * The applying student's current batches, and attendance in one of them.
 * Called with the student's own token, so it answers only about them.
 */
@FeignClient(name = "batch-service", fallbackFactory = BatchClient.Fallback.class)
public interface BatchClient {

    @GetMapping("/api/batches/mine")
    List<BatchSummary> myBatches();

    @GetMapping("/api/attendance/students/{studentId}")
    Attendance attendance(@PathVariable("studentId") Long studentId, @RequestParam("batchId") Long batchId);

    record BatchSummary(Long id, Long courseId, String courseTitle, String status) {
    }

    record Attendance(int attendedSessions, int totalSessions, BigDecimal attendancePercent) {
    }

    /** Null answers: the eligibility check reports "could not be checked" and refuses. */
    @Slf4j
    @Component
    class Fallback implements FallbackFactory<BatchClient> {

        @Override
        public BatchClient create(Throwable cause) {
            return new BatchClient() {
                @Override
                public List<BatchSummary> myBatches() {
                    log.warn("batch-service unreachable listing a student's batches", cause);
                    return null;
                }

                @Override
                public Attendance attendance(Long studentId, Long batchId) {
                    log.warn("batch-service unreachable reading attendance of student {}", studentId, cause);
                    return null;
                }
            };
        }
    }
}
