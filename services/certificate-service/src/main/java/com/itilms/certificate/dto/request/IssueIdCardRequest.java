package com.itilms.certificate.dto.request;

import java.time.LocalDate;

import com.itilms.certificate.entity.IdCardPersonType;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Issue an identity card. A student or trainer is named by their admission-service id; "
        + "other staff are typed in.")
public record IssueIdCardRequest(

        @NotNull(message = "Choose student, trainer or staff")
        IdCardPersonType personType,

        @Schema(description = "Student id or trainer id; leave empty for staff")
        Long personId,

        @Schema(description = "Staff only: the name to print")
        @Size(max = 160)
        String fullName,

        @Schema(description = "Staff only: the title under the name, for example Accountant")
        @Size(max = 120)
        String designation,

        @Schema(description = "Students only: the batch to print on the card")
        Long batchId,

        @Schema(description = "Last day the card is valid. Empty: the batch end date for a student, otherwise one year")
        LocalDate validUntil
) {
}
