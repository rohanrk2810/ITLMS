package com.itilms.reporting.dto;

import com.itilms.reporting.service.ExportedReport;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ExportLogRequest(
        @NotNull(message = "Say which report was downloaded")
        ExportedReport report,

        @Min(0)
        int rows,

        /** What was chosen, for example the batch code or the test name. */
        @Size(max = 120)
        String detail
) {
}
