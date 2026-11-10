package com.itilms.batch.client;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import lombok.extern.slf4j.Slf4j;

/** Asks finance-service whether a student really paid for a course, before they are placed in a batch. */
@FeignClient(name = "finance-service", fallbackFactory = FinanceClient.Fallback.class)
public interface FinanceClient {

    /** Answered only for the order's owner (or staff); anyone else gets "not found". */
    @GetMapping("/api/payments/online/orders/{orderId}")
    OnlineOrder order(@PathVariable("orderId") String orderId);

    record OnlineOrder(String orderId, String status, Long studentId, Long courseId, Long batchId) {
        public boolean paid() {
            return "PAID".equals(status) || "ENROLLED".equals(status);
        }
    }

    /** No answer means no placement: nobody joins a course on a payment that cannot be confirmed. */
    @Slf4j
    @Component
    class Fallback implements FallbackFactory<FinanceClient> {

        @Override
        public FinanceClient create(Throwable cause) {
            return orderId -> {
                log.warn("finance-service unreachable while checking online order {}", orderId, cause);
                return null;
            };
        }
    }
}
