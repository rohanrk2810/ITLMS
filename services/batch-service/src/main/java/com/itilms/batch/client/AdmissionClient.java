package com.itilms.batch.client;

import java.util.List;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.itilms.common.exception.BusinessRuleException;

import lombok.extern.slf4j.Slf4j;

/** Resolves student and trainer names when enrolling or allocating. */
@FeignClient(name = "admission-service", fallbackFactory = AdmissionClient.Fallback.class)
public interface AdmissionClient {

    @PostMapping("/api/students/internal/lookup")
    List<StudentSummary> lookupStudents(@RequestBody List<Long> studentIds);

    @PostMapping("/api/trainers/internal/lookup")
    List<TrainerSummary> lookupTrainers(@RequestBody List<Long> trainerIds);

    record StudentSummary(Long id, Long userId, String studentCode, String fullName,
                          String email, String phone, String status) {
    }

    record TrainerSummary(Long id, Long userId, String employeeCode, String fullName,
                          String email, String phone, String specialization, String status) {
    }

    /**
     * Enrolment genuinely needs this data, so its fallback refuses rather than
     * degrades: an enrolment row without a student id and user id cannot notify
     * anyone or appear on a register, and writing one would create a record that
     * has to be repaired by hand later.
     *
     * <p>Trainer lookup is only decoration on a listing and returns empty.
     */
    @Slf4j
    @Component
    class Fallback implements FallbackFactory<AdmissionClient> {

        @Override
        public AdmissionClient create(Throwable cause) {
            return new AdmissionClient() {

                @Override
                public List<StudentSummary> lookupStudents(List<Long> studentIds) {
                    log.error("admission-service unreachable while resolving {} student(s)",
                            studentIds.size(), cause);
                    throw new BusinessRuleException("ADMISSION_UNAVAILABLE",
                            "Student records are temporarily unavailable, so nobody was enrolled. "
                                    + "Please try again shortly.");
                }

                @Override
                public List<TrainerSummary> lookupTrainers(List<Long> trainerIds) {
                    log.warn("admission-service unreachable while resolving trainer names");
                    return List.of();
                }
            };
        }
    }
}
