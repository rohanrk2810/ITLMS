package com.itilms.assessment.dto.response;

import java.time.Instant;
import java.util.List;

import com.itilms.assessment.entity.Submission;
import com.itilms.assessment.entity.SubmissionFile;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A student's submitted work")
public record SubmissionResponse(
        Long id,
        Long assignmentId,
        Long studentId,
        String studentName,
        String textAnswer,
        Instant submittedAt,

        @Schema(description = "How many times this student has handed work in for this assignment")
        int submissionCount,

        @Schema(description = "SUBMITTED, LATE, EVALUATED or RETURNED")
        String status,

        Integer marks,
        String feedback,
        Instant evaluatedAt,
        List<FileResponse> files
) {

    @Schema(description = "A file handed in. Download it through file-service.")
    public record FileResponse(String fileRef, String fileName, String contentType,
                               Long sizeBytes, Instant uploadedAt) {

        static FileResponse from(SubmissionFile f) {
            return new FileResponse(f.getFileRef(), f.getFileName(), f.getContentType(),
                    f.getSizeBytes(), f.getUploadedAt());
        }
    }

    public static SubmissionResponse from(Submission s) {
        return new SubmissionResponse(
                s.getId(), s.getAssignmentId(), s.getStudentId(), s.getStudentName(),
                s.getTextAnswer(), s.getSubmittedAt(), s.getSubmissionCount(), s.getStatus().name(),
                s.getMarks(), s.getFeedback(), s.getEvaluatedAt(),
                s.getFiles().stream().map(FileResponse::from).toList());
    }
}
