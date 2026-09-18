package com.itilms.batch.dto.request;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Save the register for a session (Doc S6.9, S11).
 *
 * <p>The whole register is submitted at once rather than one student at a time.
 * A trainer ticking through forty names should not be able to leave the record
 * half saved because their connection dropped on student twenty-three.
 */
@Schema(description = "Mark attendance for a session")
public record MarkAttendanceRequest(

        @NotEmpty(message = "At least one attendance entry is required")
        @Valid
        List<Entry> entries,

        @Schema(description = "Required when correcting a register that was already saved. "
                + "Doc S14 requires corrections to be audited.")
        @Size(max = 255)
        String correctionReason
) {

    @Schema(description = "One student's mark")
    public record Entry(

            @NotNull(message = "Student is required")
            Long studentId,

            @Schema(allowableValues = {"PRESENT", "ABSENT", "LATE", "EXCUSED"})
            @NotBlank(message = "Status is required")
            String status,

            @Size(max = 255)
            String remark
    ) {
    }
}
