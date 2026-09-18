package com.itilms.admission.dto.response;

import com.itilms.admission.entity.Student;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Just enough to identify a student in a list.
 *
 * <p>This is the shape other services ask for when rendering a batch roster, an
 * attendance sheet or a fee report. Sending the full profile — guardian details,
 * home address, date of birth — to eleven services that only wanted a name is
 * how personal data ends up somewhere nobody intended it to be.
 */
@Schema(description = "Condensed student record")
public record StudentSummaryResponse(
        Long id,
        Long userId,
        String studentCode,
        String fullName,
        String email,
        String phone,
        String status
) {

    public static StudentSummaryResponse from(Student student) {
        return new StudentSummaryResponse(
                student.getId(),
                student.getUserId(),
                student.getStudentCode(),
                student.getFullName(),
                student.getEmail(),
                student.getPhone(),
                student.getStatus().name());
    }
}
