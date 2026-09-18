package com.itilms.assessment.client;

import java.util.List;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import lombok.extern.slf4j.Slf4j;

/**
 * Who is in which batch, and who teaches it.
 *
 * <p>assessment-service has no roster of its own. Both questions it asks here
 * are authorization questions - may this student take this test, may this
 * trainer mark this work - so batch-service, which owns enrolment, answers them.
 */
@FeignClient(name = "batch-service", fallbackFactory = BatchClient.Fallback.class)
public interface BatchClient {

    /** The signed-in student's or trainer's own batches, scoped by their token. */
    @GetMapping("/api/batches/mine")
    List<BatchSummary> myBatches();

    @GetMapping("/api/batches/internal/{batchId}/enrolled/{studentId}")
    EnrollmentCheck isEnrolled(@PathVariable("batchId") Long batchId,
                               @PathVariable("studentId") Long studentId);

    record BatchSummary(Long id, String batchCode, String name, Long courseId,
                        String courseTitle, Long trainerId, String status) {
    }

    record EnrollmentCheck(boolean enrolled) {
    }

    /**
     * Both fallbacks fail closed, because both answers are permissions.
     *
     * <p>An empty batch list means a trainer is shown no work to mark and a
     * student no tests to take - a visible, temporary outage they will retry.
     * Answering "yes, enrolled" while batch-service is unreachable would instead
     * let anyone with a token sit a test for a batch they are not in, and that
     * mistake is written into a result nobody thinks to question later.
     */
    @Slf4j
    @Component
    class Fallback implements FallbackFactory<BatchClient> {

        @Override
        public BatchClient create(Throwable cause) {
            return new BatchClient() {

                @Override
                public List<BatchSummary> myBatches() {
                    log.warn("batch-service unreachable while listing the caller's batches", cause);
                    return List.of();
                }

                @Override
                public EnrollmentCheck isEnrolled(Long batchId, Long studentId) {
                    log.error("batch-service unreachable while checking whether student {} is in batch {}",
                            studentId, batchId, cause);
                    return new EnrollmentCheck(false);
                }
            };
        }
    }
}
