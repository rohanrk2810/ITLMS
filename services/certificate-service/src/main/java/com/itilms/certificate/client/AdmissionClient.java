package com.itilms.certificate.client;

import java.util.List;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    /** The photo the student gave at admission. A 404 (no photo yet) or an unreachable service comes back as null. */
    @GetMapping("/api/students/{id}/photo")
    ResponseEntity<byte[]> studentPhoto(@PathVariable("id") Long studentId);

    @PostMapping("/api/trainers/internal/lookup")
    List<Trainer> lookupTrainers(@RequestBody List<Long> trainerIds);

    record Trainer(Long id, Long userId, String employeeCode, String fullName, String status) {
    }

    @Slf4j
    @Component
    class Fallback implements FallbackFactory<AdmissionClient> {

        @Override
        public AdmissionClient create(Throwable cause) {
            return new AdmissionClient() {
                @Override
                public List<Student> lookup(List<Long> studentIds) {
                    log.warn("admission-service unreachable looking up {} student(s)", studentIds.size(), cause);
                    return List.of();
                }

                @Override
                public ResponseEntity<byte[]> studentPhoto(Long studentId) {
                    log.debug("No photo for student {}: admission-service answered with an error", studentId, cause);
                    return null;
                }

                @Override
                public List<Trainer> lookupTrainers(List<Long> trainerIds) {
                    log.warn("admission-service unreachable looking up {} trainer(s)", trainerIds.size(), cause);
                    return List.of();
                }
            };
        }
    }
}
