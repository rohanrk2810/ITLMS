package com.itilms.liveclass.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One student's answer to one live question. A student answers once. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "live_question_answers")
public class LiveQuestionAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "student_id")
    private Long studentId;

    @Column(name = "display_name")
    private String displayName;

    /** JSON array of the indexes picked. */
    private String selected;

    @Column(name = "answer_text")
    private String answerText;

    private String code;

    private String language;

    /** Null when nothing can mark it automatically (coding, open questions, short answers with no key). */
    private Boolean correct;

    @Column(name = "awarded_marks")
    private Integer awardedMarks;

    /** True when answered from the recording after the question had closed. */
    @Column(name = "via_recording", nullable = false)
    private boolean viaRecording;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;
}
