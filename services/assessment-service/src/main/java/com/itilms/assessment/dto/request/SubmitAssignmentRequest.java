package com.itilms.assessment.dto.request;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A student's answer: text, files, or both.
 *
 * <p>The document models a single file URL (S10) while S6.10 lets students
 * submit "files/text"; both are accepted here, and the service refuses an
 * entirely empty submission.
 */
@Schema(description = "Work handed in for an assignment")
public record SubmitAssignmentRequest(

        @Size(max = 20000, message = "The written answer is too long")
        String textAnswer,

        @Valid
        @Size(max = 10, message = "At most 10 files per submission")
        List<FileRef> files
) {

    /**
     * A file already uploaded to file-service.
     *
     * <p>Uploads go there directly, so a large file never travels through this
     * service, and the type and size rules from Doc S17 are enforced in one place.
     */
    @Schema(description = "A file already uploaded to file-service")
    public record FileRef(
            @NotBlank(message = "File reference is required")
            @Size(max = 120)
            String fileRef,

            @Size(max = 255)
            String fileName,

            @Size(max = 120)
            String contentType,

            Long sizeBytes
    ) {
    }
}
