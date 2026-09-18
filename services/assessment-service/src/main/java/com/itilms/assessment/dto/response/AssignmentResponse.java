package com.itilms.assessment.dto.response;

import java.time.Instant;

import com.itilms.assessment.entity.Assignment;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "An assignment, with how the batch is doing on it")
public record AssignmentResponse(
        Long id,
        Long batchId,
        Long courseId,
        String title,
        String instructions,
        String attachmentRef,
        Instant dueAt,
        int maxMarks,
        boolean allowLate,
        boolean mandatory,
        String status,
        Long trainerId,
        Instant publishedAt,

        @Schema(description = "True once the deadline has passed")
        boolean overdue,

        @Schema(description = "How many students have handed work in; null on a student's view")
        Long submissionCount,

        @Schema(description = "How many have been marked; null on a student's view")
        Long evaluatedCount,

        @Schema(description = "The signed-in student's own submission, when there is one")
        SubmissionResponse mySubmission
) {

    public static AssignmentResponse of(Assignment a, Long submissions, Long evaluated,
                                        SubmissionResponse mine) {
        return new AssignmentResponse(
                a.getId(), a.getBatchId(), a.getCourseId(), a.getTitle(), a.getInstructions(),
                a.getAttachmentRef(), a.getDueAt(), a.getMaxMarks(), a.isAllowLate(), a.isMandatory(),
                a.getStatus().name(), a.getTrainerId(), a.getPublishedAt(),
                a.isOverdue(Instant.now()), submissions, evaluated, mine);
    }

    public static AssignmentResponse from(Assignment a) {
        return of(a, null, null, null);
    }
}
