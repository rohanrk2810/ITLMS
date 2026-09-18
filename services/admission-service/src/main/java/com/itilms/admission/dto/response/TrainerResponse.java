package com.itilms.admission.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.itilms.admission.entity.Trainer;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Trainer profile")
public record TrainerResponse(
        Long id,
        Long userId,
        String employeeCode,
        String fullName,
        String email,
        String phone,
        String specialization,
        String qualification,
        BigDecimal experienceYears,
        String bio,
        LocalDate joinedOn,
        String status,
        @Schema(description = "Courses this trainer is cleared to teach")
        List<Long> courseIds
) {

    public static TrainerResponse from(Trainer trainer, List<Long> courseIds) {
        return new TrainerResponse(
                trainer.getId(),
                trainer.getUserId(),
                trainer.getEmployeeCode(),
                trainer.getFullName(),
                trainer.getEmail(),
                trainer.getPhone(),
                trainer.getSpecialization(),
                trainer.getQualification(),
                trainer.getExperienceYears(),
                trainer.getBio(),
                trainer.getJoinedOn(),
                trainer.getStatus().name(),
                courseIds == null ? List.of() : courseIds);
    }
}
