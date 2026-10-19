package com.itilms.course.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Create or update a lesson (Doc S6.8).
 *
 * <p>Exactly one of {@code contentUrl}, {@code contentFileRef} or
 * {@code textContent} must carry the material; the service rejects a lesson
 * with none, so students never open an empty player.
 */
@Schema(description = "Learning material")
public record LessonRequest(

        @NotBlank(message = "Lesson title is required")
        @Size(max = 160)
        String title,

        @Schema(example = "VIDEO", allowableValues = {"VIDEO", "PDF", "NOTE", "LINK", "TEXT"})
        @NotBlank(message = "Lesson type is required")
        String type,

        @Schema(description = "Video source or external link")
        @Size(max = 600)
        String contentUrl,

        @Schema(description = "file-service handle for uploaded material")
        @Size(max = 64)
        String contentFileRef,

        @Schema(description = "Rich text written directly into the lesson")
        String textContent,

        @Min(value = 0, message = "Duration cannot be negative")
        Integer durationMinutes,

        @Min(value = 1, message = "Sequence starts at 1")
        Integer sequenceNo,

        @Schema(description = "Readable from the public catalog without enrolment")
        Boolean preview,

        @Schema(description = "Counts toward course completion. Default true.")
        Boolean mandatory,

        @Schema(description = "Adds a practice editor to the lesson. Omit for none.",
                allowableValues = {"JAVA", "PYTHON", "C", "CPP", "CSHARP", "SQL"})
        @Size(max = 10)
        String codeLanguage,

        @Schema(description = "What the practice editor opens with. Needs codeLanguage.")
        @Size(max = 20000, message = "Starter code is limited to 20,000 characters")
        String starterCode,

        @Schema(description = "Practice editor: let the student choose the language. Needs codeLanguage.")
        Boolean allowLanguageChoice,

        @Schema(description = "VIDEO lessons only: the student's camera checks that a face is in view while it plays")
        Boolean videoMonitoring,

        @Schema(description = "Pause the video when the face has been missing for faceGraceSeconds. Needs videoMonitoring.")
        Boolean pauseOnNoFace,

        @Schema(description = "A video paused for a missing face resumes by itself when the face is back. "
                + "Needs pauseOnNoFace; otherwise the student presses Resume.")
        Boolean autoResume,

        @Schema(description = "How long the face may be missing before the video pauses (2-60, default 5)")
        @Min(value = 2, message = "Grace is at least 2 seconds")
        @Max(value = 60, message = "Grace is at most 60 seconds")
        Integer faceGraceSeconds
) {

    /** A lesson request that says nothing about video monitoring (it stays off). */
    public LessonRequest(String title, String type, String contentUrl, String contentFileRef, String textContent,
                         Integer durationMinutes, Integer sequenceNo, Boolean preview, Boolean mandatory,
                         String codeLanguage, String starterCode, Boolean allowLanguageChoice) {
        this(title, type, contentUrl, contentFileRef, textContent, durationMinutes, sequenceNo, preview, mandatory,
                codeLanguage, starterCode, allowLanguageChoice, null, null, null, null);
    }

    /** A lesson request that says nothing about language choice (the choice stays off). */
    public LessonRequest(String title, String type, String contentUrl, String contentFileRef, String textContent,
                         Integer durationMinutes, Integer sequenceNo, Boolean preview, Boolean mandatory,
                         String codeLanguage, String starterCode) {
        this(title, type, contentUrl, contentFileRef, textContent, durationMinutes, sequenceNo, preview, mandatory,
                codeLanguage, starterCode, null);
    }

    public boolean videoMonitoringFor(boolean isVideo) {
        return isVideo && Boolean.TRUE.equals(videoMonitoring);
    }

    public boolean pauseOnNoFaceFor(boolean isVideo) {
        return videoMonitoringFor(isVideo) && Boolean.TRUE.equals(pauseOnNoFace);
    }

    public boolean autoResumeFor(boolean isVideo) {
        return pauseOnNoFaceFor(isVideo) && Boolean.TRUE.equals(autoResume);
    }

    public int faceGraceOrDefault() {
        return faceGraceSeconds == null ? 5 : faceGraceSeconds;
    }

    public boolean previewOrDefault() {
        return Boolean.TRUE.equals(preview);
    }

    public boolean mandatoryOrDefault() {
        return mandatory == null || mandatory;
    }
}
