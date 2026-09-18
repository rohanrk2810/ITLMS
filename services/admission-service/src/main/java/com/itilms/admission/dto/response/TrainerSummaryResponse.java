package com.itilms.admission.dto.response;

import com.itilms.admission.entity.Trainer;

import io.swagger.v3.oas.annotations.media.Schema;

/** Condensed trainer record, used by batch-service and the timetable. */
@Schema(description = "Condensed trainer record")
public record TrainerSummaryResponse(
        Long id,
        Long userId,
        String employeeCode,
        String fullName,
        String email,
        String phone,
        String specialization,
        String status
) {

    public static TrainerSummaryResponse from(Trainer trainer) {
        return new TrainerSummaryResponse(
                trainer.getId(),
                trainer.getUserId(),
                trainer.getEmployeeCode(),
                trainer.getFullName(),
                trainer.getEmail(),
                trainer.getPhone(),
                trainer.getSpecialization(),
                trainer.getStatus().name());
    }
}
