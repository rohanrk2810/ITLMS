package com.itilms.reporting.client;

import java.util.List;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import lombok.extern.slf4j.Slf4j;

/** A student's profile: name, code and contact details. */
@FeignClient(name = "admission-service", fallbackFactory = AdmissionClient.Fallback.class)
public interface AdmissionClient {

    @PostMapping("/api/students/internal/lookup")
    List<StudentSummary> lookup(@RequestBody List<Long> studentIds);

    record StudentSummary(Long id, Long userId, String studentCode, String fullName, String email, String phone,
                          String status) {
    }

    @Slf4j
    @Component
    class Fallback implements FallbackFactory<AdmissionClient> {

        @Override
        public AdmissionClient create(Throwable cause) {
            return studentIds -> {
                log.warn("admission-service unavailable for a progress report: {}", cause.toString());
                return null;
            };
        }
    }
}