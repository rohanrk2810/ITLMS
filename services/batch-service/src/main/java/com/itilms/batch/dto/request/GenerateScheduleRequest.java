package com.itilms.batch.dto.request;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Fill a batch's timetable in one go.
 *
 * <p>Creating sixty lectures by hand, one form at a time, is where timetables go
 * wrong: a coordinator mistypes a date, skips a week, or gives up halfway. This
 * expands the batch's weekly pattern across a date range and skips the holidays
 * it is given.
 */
@Schema(description = "Generate a batch's sessions from its weekly pattern")
public record GenerateScheduleRequest(

        @NotNull(message = "Start date is required")
        LocalDate from,

        @NotNull(message = "End date is required")
        LocalDate to,

        @Schema(description = "Dates to skip - public holidays, institute closures")
        List<LocalDate> excludeDates,

        @Schema(description = "Topic applied to every generated session; edit individually afterwards")
        String defaultTopic
) {

    public List<LocalDate> excludeDatesOrEmpty() {
        return excludeDates == null ? List.of() : excludeDates;
    }
}
