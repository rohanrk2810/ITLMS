package com.itilms.course.entity;

import com.itilms.common.entity.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One piece of learning material (Doc S6.8). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "lessons")
public class Lesson extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "module_id", nullable = false)
    private Long moduleId;

    @Column(nullable = false, length = 160)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LessonType type;

    /** Video source or external link. */
    @Column(name = "content_url", length = 600)
    private String contentUrl;

    /** file-service handle, for uploaded material. */
    @Column(name = "content_file_ref", length = 64)
    private String contentFileRef;

    @Column(name = "text_content", columnDefinition = "text")
    private String textContent;

    /** Drives the estimated course length shown to students. */
    @Column(name = "duration_minutes", nullable = false)
    @Builder.Default
    private Integer durationMinutes = 0;

    @Column(name = "sequence_no", nullable = false)
    private Integer sequenceNo;

    /**
     * Readable without enrolment, from the public catalog.
     *
     * <p>The one exception to Doc S14's rule that content requires enrolment —
     * and the exception is the point: a prospective student needs to see a
     * sample before paying for the rest.
     */
    @Column(name = "is_preview", nullable = false)
    @Builder.Default
    private boolean preview = false;

    /** Only mandatory lessons count toward course completion (Doc S7.3). */
    @Column(name = "is_mandatory", nullable = false)
    @Builder.Default
    private boolean mandatory = true;

    /** True when the lesson has something a student can actually open. */
    public boolean hasContent() {
        return (contentUrl != null && !contentUrl.isBlank())
                || (contentFileRef != null && !contentFileRef.isBlank())
                || (textContent != null && !textContent.isBlank());
    }
}
