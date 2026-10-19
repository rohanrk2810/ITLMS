package com.itilms.course.dto.response;

import com.itilms.course.entity.Lesson;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A lesson, with the content fields redacted when the caller has not earned
 * access to them.
 *
 * <p>Doc S14 is explicit that content requires enrolment. Enforcing that by
 * blanking the fields on the way out — rather than by hiding a button in the UI
 * — means the rule holds for anyone calling the API directly, which is the only
 * way it is actually a rule.
 */
@Schema(description = "Lesson")
public record LessonResponse(
        Long id,
        Long moduleId,
        String title,
        String type,
        @Schema(description = "Null unless the caller is enrolled or the lesson is a preview")
        String contentUrl,
        String contentFileRef,
        String textContent,
        Integer durationMinutes,
        Integer sequenceNo,
        boolean preview,
        boolean mandatory,
        @Schema(description = "Whether this caller may open the material")
        boolean accessible,
        @Schema(description = "Present only when the caller is an enrolled student")
        Boolean completed,
        Integer watchedSeconds,
        @Schema(description = "Language of the lesson's practice editor; null when it has none. "
                + "Null too when the lesson is locked - starter code is material.")
        String codeLanguage,
        String starterCode,
        @Schema(description = "Practice editor: the student may pick another language")
        boolean allowLanguageChoice,
        @Schema(description = "The student's camera checks a face is in view while this video plays")
        boolean videoMonitoring,
        boolean pauseOnNoFace,
        boolean autoResume,
        Integer faceGraceSeconds,
        @Schema(description = "Enrolled student only: share of the video actually watched, 0-100")
        Integer watchedPercent,
        Integer faceAwaySeconds,
        Integer facePauses
) {

    /** Full content, for an enrolled student or for staff. */
    public static LessonResponse unlocked(Lesson lesson, Boolean completed, Integer watchedSeconds) {
        return unlocked(lesson, completed, watchedSeconds, null);
    }

    /** As above, with what the enrolled student has actually watched (null for staff and previews). */
    public static LessonResponse unlocked(Lesson lesson, Boolean completed, Integer watchedSeconds,
                                          com.itilms.course.entity.LessonProgress progress) {
        return new LessonResponse(
                lesson.getId(), lesson.getModuleId(), lesson.getTitle(), lesson.getType().name(),
                lesson.getContentUrl(), lesson.getContentFileRef(), lesson.getTextContent(),
                lesson.getDurationMinutes(), lesson.getSequenceNo(),
                lesson.isPreview(), lesson.isMandatory(), true, completed, watchedSeconds,
                lesson.getCodeLanguage() == null ? null : lesson.getCodeLanguage().name(),
                lesson.getStarterCode(), lesson.isAllowLanguageChoice(),
                lesson.isVideoMonitoring(), lesson.isPauseOnNoFace(), lesson.isAutoResume(),
                lesson.getFaceGraceSeconds(),
                progress == null ? null : progress.watchedPercent(),
                progress == null ? null : progress.getFaceAwaySeconds(),
                progress == null ? null : progress.getFacePauses());
    }

    /**
     * Title and shape only.
     *
     * <p>The curriculum stays visible — a prospective student should see what a
     * course covers — but the material itself does not travel.
     */
    public static LessonResponse locked(Lesson lesson) {
        return new LessonResponse(
                lesson.getId(), lesson.getModuleId(), lesson.getTitle(), lesson.getType().name(),
                null, null, null,
                lesson.getDurationMinutes(), lesson.getSequenceNo(),
                lesson.isPreview(), lesson.isMandatory(), false, null, null, null, null, false,
                false, false, false, 5, null, null, null);
    }
}
