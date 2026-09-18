package com.itilms.certificate.client;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import lombok.extern.slf4j.Slf4j;

/** Whether the course's fees are paid. */
@FeignClient(name = "finance-service", fallbackFactory = FinanceClient.Fallback.class)
public interface FinanceClient {

    @GetMapping("/api/fees/students/{studentId}")
    List<FeePlan> plans(@PathVariable("studentId") Long studentId);

    record FeePlan(Long id, Long courseId, BigDecimal netFee, BigDecimal outstanding, String status) {
    }

    /**
     * Null, not an empty list: "no fee plan" means nothing is owed, while
     * "could not ask" must not be read as the same thing.
     */
    @Slf4j
    @Component
    class Fallback implements FallbackFactory<FinanceClient> {

        @Override
        public FinanceClient create(Throwable cause) {
            return studentId -> {
                log.warn("finance-service unreachable reading fees of student {}", studentId, cause);
                return null;
            };
        }
    }
}
