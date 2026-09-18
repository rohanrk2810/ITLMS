package com.itilms.finance.client;

import java.util.List;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import lombok.extern.slf4j.Slf4j;

/** Reads a student's name and code when a fee plan is raised by hand. */
@FeignClient(name = "admission-service", fallbackFactory = AdmissionClient.Fallback.class)
public interface AdmissionClient {

    @PostMapping("/api/students/internal/lookup")
    List<StudentSummary> lookup(@RequestBody List<Long> studentIds);

    record StudentSummary(Long id, Long userId, String studentCode, String fullName, String status) {
    }

    /**
     * An empty answer, which the caller turns into "no such student".
     *
     * <p>Raising a fee plan for a student who cannot be confirmed to exist
     * would create a debt nobody can be asked to pay. Refusing until
     * admission-service is back is the safer failure.
     */
    @Slf4j
    @Component
    class Fallback implements FallbackFactory<AdmissionClient> {

        @Override
        public AdmissionClient create(Throwable cause) {
            return ids -> {
                log.warn("admission-service unreachable while looking up {} student(s)", ids.size(), cause);
                return List.of();
            };
        }
    }
}
