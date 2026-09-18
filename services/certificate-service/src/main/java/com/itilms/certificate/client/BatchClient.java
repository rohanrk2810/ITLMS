package com.itilms.certificate.client;

import java.math.BigDecimal;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import lombok.extern.slf4j.Slf4j;

/** Attendance in the batch (Doc S7.3 "attendance criteria met"). */
@FeignClient(name = "batch-service", fallbackFactory = BatchClient.Fallback.class)
public interface BatchClient {

    @GetMapping("/api/attendance/students/{studentId}")
    AttendanceSummary attendance(@PathVariable("studentId") Long studentId, @RequestParam("batchId") Long batchId);

    record AttendanceSummary(Long studentId, Long batchId, int attendedSessions, int totalSessions,
                             BigDecimal attendancePercent) {
    }

    @Slf4j
    @Component
    class Fallback implements FallbackFactory<BatchClient> {

        @Override
        public BatchClient create(Throwable cause) {
            return (studentId, batchId) -> {
                log.warn("batch-service unreachable reading attendance of student {}", studentId, cause);
                return null;
            };
        }
    }
}
