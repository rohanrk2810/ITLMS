package com.itilms.certificate.client;

import java.util.List;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import lombok.extern.slf4j.Slf4j;

/** The name printed on the certificate, from the record that owns it. */
@FeignClient(name = "admission-service", fallbackFactory = AdmissionClient.Fallback.class)
public interface AdmissionClient {

    @PostMapping("/api/students/internal/lookup")
    List<Student> lookup(@RequestBody List<Long> studentIds);

    record Student(Long id, Long userId, String studentCode, String fullName, String status) {
    }

    @Slf4j
    @Component
    class Fallback implements FallbackFactory<AdmissionClient> {

        @Override
        public AdmissionClient create(Throwable cause) {
            return ids -> {
                log.warn("admission-service unreachable looking up {} student(s)", ids.size(), cause);
                return List.of();
            };
        }
    }
}
