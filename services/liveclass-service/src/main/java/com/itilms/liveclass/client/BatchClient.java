package com.itilms.liveclass.client;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import lombok.extern.slf4j.Slf4j;

/**
 * Reads the timetable that live classes are attached to.
 *
 * <p>liveclass-service owns rooms and room time; it owns neither the schedule
 * nor the register. Both are batch-service facts, asked for here rather than
 * copied, so there is one answer to "when is this class and who is in it".
 */
@FeignClient(name = "batch-service", fallbackFactory = BatchClient.Fallback.class)
public interface BatchClient {

    @GetMapping("/api/sessions/{id}")
    SessionDetail session(@PathVariable("id") Long id);

    @GetMapping("/api/batches/internal/{batchId}/enrolled/{studentId}")
    EnrollmentCheck isEnrolled(@PathVariable("batchId") Long batchId,
                               @PathVariable("studentId") Long studentId);

    /** The signed-in student's or trainer's own batches; scoped by their token. */
    @GetMapping("/api/batches/mine")
    List<BatchSummary> myBatches();

    /** The slice of batch-service's session response this service actually uses. */
    record SessionDetail(
            Long id,
            Long batchId,
            String batchCode,
            String courseTitle,
            Long trainerId,
            String trainerName,
            LocalDate sessionDate,
            LocalTime startTime,
            LocalTime endTime,
            String topic,
            String mode,
            String status,
            boolean live
    ) {
    }

    record EnrollmentCheck(boolean enrolled) {
    }

    record BatchSummary(Long id, String batchCode, String courseTitle, String status) {
    }

    /**
     * What to do when batch-service cannot be reached.
     *
     * <p>The two cases here are deliberately opposite, because the cost of being
     * wrong is opposite.
     *
     * <p>A missing session or batch list is a degraded page: the caller gets
     * null or an empty list, sees "we could not load your classes", and tries
     * again. Nothing is granted that should not be.
     *
     * <p>{@code isEnrolled} is an authorization decision, so it fails closed.
     * Returning "enrolled" on a timeout would hand a join token to anyone who
     * asked while batch-service was down - a student from another batch, or a
     * former student whose place was dropped last term. Being unable to prove
     * someone belongs in the room is not the same as them belonging in it.
     */
    @Slf4j
    @Component
    class Fallback implements FallbackFactory<BatchClient> {

        @Override
        public BatchClient create(Throwable cause) {
            return new BatchClient() {

                @Override
                public SessionDetail session(Long id) {
                    log.warn("batch-service unreachable while reading session {}", id, cause);
                    return null;
                }

                @Override
                public EnrollmentCheck isEnrolled(Long batchId, Long studentId) {
                    log.error("batch-service unreachable while checking whether student {} is in "
                            + "batch {} - refusing entry", studentId, batchId, cause);
                    return new EnrollmentCheck(false);
                }

                @Override
                public List<BatchSummary> myBatches() {
                    log.warn("batch-service unreachable while listing the caller's batches", cause);
                    return List.of();
                }
            };
        }
    }
}
