package com.itilms.reporting.client;

import java.time.LocalDate;
import java.util.List;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import lombok.extern.slf4j.Slf4j;

/**
 * A student's enrolments, and the caller's own batches (which is how a trainer's right to see a student is checked).
 * Both fail closed or soft as the caller needs: null means "could not find out".
 */
@FeignClient(name = "batch-service", fallbackFactory = BatchClient.Fallback.class)
public interface BatchClient {

    @GetMapping("/api/batches/internal/students/{studentId}/enrollments")
    List<Enrollment> enrollmentsOf(@PathVariable("studentId") Long studentId);

    /** The signed-in trainer's or student's own batches, scoped by their token. */
    @GetMapping("/api/batches/mine")
    List<MyBatch> myBatches();

    record Enrollment(Long enrollmentId, String status, Long batchId, String batchCode, String batchName,
                      String batchStatus, Long courseId, String courseTitle, String trainerName, String mode,
                      LocalDate startDate, LocalDate endDate) {

        public boolean isActive() {
            return "ACTIVE".equals(status);
        }
    }

    record MyBatch(Long id) {
    }

    @Slf4j
    @Component
    class Fallback implements FallbackFactory<BatchClient> {

        @Override
        public BatchClient create(Throwable cause) {
            return new BatchClient() {
                @Override
                public List<Enrollment> enrollmentsOf(Long studentId) {
                    log.warn("batch-service unavailable for a progress report: {}", cause.toString());
                    return null;
                }

                @Override
                public List<MyBatch> myBatches() {
                    log.warn("batch-service unavailable while checking a trainer's batches: {}", cause.toString());
                    return null;
                }
            };
        }
    }
}