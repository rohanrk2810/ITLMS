package com.itilms.reporting.progress;

import java.time.Instant;
import java.util.List;

import com.itilms.reporting.client.AssessmentClient;
import com.itilms.reporting.client.BatchClient;
import com.itilms.reporting.client.CourseClient;
import com.itilms.reporting.client.LiveClient;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Everything the institute knows about how one student is getting on, in one place: who they are, where they are
 * enrolled, how far they have got, how they have done, and what they should do next.
 *
 * <p>A section is null when the service that owns it could not be reached; its name is then listed in
 * {@code unavailable}, and the rest of the report is still returned. Nothing here is stored: it is worked out
 * from the owning services each time it is asked for.
 */
@Schema(description = "A student's complete record and progress report")
public record StudentProgressReport(
        Long studentId,
        Profile profile,
        Instant generatedAt,
        @Schema(description = "The headline percentages, each with a band: GOOD, FAIR, LOW or NONE when there is no data yet")
        List<Indicator> indicators,
        List<BatchClient.Enrollment> enrollments,
        List<CourseClient.CourseProgress> courses,
        Attendance attendance,
        LiveClient.Participation liveClasses,
        AssessmentClient.Tests tests,
        AssessmentClient.Coding coding,
        AssessmentClient.Assignments assignments,
        @Schema(description = "What to do next, most important first, worked out from the numbers above")
        List<Suggestion> suggestions,
        @Schema(description = "Sections that could not be loaded just now")
        List<String> unavailable
) {

    public record Profile(String fullName, String studentCode, String email, String phone, String status) {
    }

    /** One headline number, e.g. "Attendance 88%". */
    public record Indicator(String key, String label, Integer percent, String band, String detail) {
    }

    public record BatchAttendance(Long batchId, String batchCode, long attended, long counted, Integer percent) {
    }

    @Schema(description = "Class attendance as the register records it. Excused absences are left out of the count.")
    public record Attendance(Integer percent, long attended, long counted, long absent, long excused,
                             List<BatchAttendance> byBatch) {
    }

    /** One thing to do. {@code priority} 1 is most urgent; {@code link} is a page in the app. */
    public record Suggestion(String type, int priority, String title, String detail, String link) {
    }
}
