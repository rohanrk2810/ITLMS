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

    /** Only the name is needed, to print and list which batch the certificate is for. */
    @GetMapping("/api/batches/{id}")
    BatchInfo batch(@PathVariable("id") Long id);

    /** The courses a student holds in a batch, bought or came with it. Null when the batch could not be asked. */
    @GetMapping("/api/batches/internal/{batchId}/students/{studentId}/courses")
    java.util.List<Long> coursesOfStudent(@PathVariable("batchId") Long batchId,
                                          @PathVariable("studentId") Long studentId);

    record BatchInfo(Long id, String batchCode, String name, java.time.LocalDate endDate,
                     java.util.List<BatchCourse> courses) {
        public BatchInfo(Long id, String batchCode, String name) {
            this(id, batchCode, name, null, null);
        }

        public BatchInfo(Long id, String batchCode, String name, java.time.LocalDate endDate) {
            this(id, batchCode, name, endDate, null);
        }
    }

    /** One course a batch holds. */
    record BatchCourse(Long courseId, String courseTitle) {
    }

    record AttendanceSummary(Long studentId, Long batchId, int attendedSessions, int totalSessions,
                             BigDecimal attendancePercent) {
    }

    @Slf4j
    @Component
    class Fallback implements FallbackFactory<BatchClient> {

        @Override
        public BatchClient create(Throwable cause) {
            return new BatchClient() {
                @Override
                public AttendanceSummary attendance(Long studentId, Long batchId) {
                    log.warn("batch-service unreachable reading attendance of student {}", studentId, cause);
                    return null;
                }

                @Override
                public java.util.List<Long> coursesOfStudent(Long batchId, Long studentId) {
                    log.warn("batch-service unreachable reading the courses of student {} in batch {}",
                            studentId, batchId, cause);
                    return null;
                }

                @Override
                public BatchInfo batch(Long id) {
                    log.warn("batch-service unreachable reading batch {}", id, cause);
                    return null;
                }
            };
        }
    }
}
