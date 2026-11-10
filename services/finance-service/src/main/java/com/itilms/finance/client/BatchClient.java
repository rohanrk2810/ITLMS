package com.itilms.finance.client;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/** Whether a course has a seat, and placing a student who has paid. */
@FeignClient(name = "batch-service", path = "/api/course-requests", fallbackFactory = BatchClient.Fallback.class)
public interface BatchClient {

    /** Null (404 or unreachable) when no batch of the course has a free seat. */
    @GetMapping("/open-batch")
    OpenBatch openBatch(@RequestParam("courseId") Long courseId,
                        @RequestParam(value = "batchId", required = false) Long batchId);

    @PostMapping("/paid-join")
    PaidJoin paidJoin(@RequestBody PaidJoinRequest request);

    record OpenBatch(Long batchId, String batchCode, long seatsLeft) {
    }

    record PaidJoinRequest(Long courseId, String orderId) {
    }

    record PaidJoin(Long batchId, String batchCode, boolean alreadyEnrolled) {
    }

    /** Both fail closed: no seat means no sale, and a failed placement leaves the order PAID for the institute. */
    @Component
    class Fallback implements FallbackFactory<BatchClient> {
        @Override
        public BatchClient create(Throwable cause) {
            return new BatchClient() {
                @Override
                public OpenBatch openBatch(Long courseId, Long batchId) {
                    return null;
                }

                @Override
                public PaidJoin paidJoin(PaidJoinRequest request) {
                    return null;
                }
            };
        }
    }
}
