package com.itilms.admission.entity;

import java.time.Instant;

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

/**
 * An enquiry, from first contact to admission or dead end (Doc S6.4, S7.1).
 *
 * <p>A lead is not a user and has no account. Most never become one — that is
 * the normal outcome, not a failure — so creating a login for every phone
 * enquiry would fill the user table with people who never sign in.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "leads")
public class Lead extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(length = 160)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private LeadSource source;

    /** Points at course-service; no foreign key across the service boundary. */
    @Column(name = "interested_course_id")
    private Long interestedCourseId;

    /** The staff member responsible. Points at identity-service. */
    @Column(name = "counselor_user_id")
    private Long counselorUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private LeadStatus status = LeadStatus.NEW;

    @Column(name = "next_follow_up_at")
    private Instant nextFollowUpAt;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "lost_reason", length = 255)
    private String lostReason;

    @Column(name = "converted_student_id")
    private Long convertedStudentId;

    @Column(name = "converted_at")
    private Instant convertedAt;

    /**
     * Records the conversion. Sets all three fields together because the
     * database constraint requires them to agree, and because a half-converted
     * lead is not a state anyone should be able to produce.
     */
    public void markConverted(Long studentId) {
        this.status = LeadStatus.CONVERTED;
        this.convertedStudentId = studentId;
        this.convertedAt = Instant.now();
        this.nextFollowUpAt = null;
    }

    public boolean isConverted() {
        return status == LeadStatus.CONVERTED;
    }

    /** True when the follow-up date has passed and nobody has acted on it. */
    public boolean isOverdue() {
        return status.isOpen() && nextFollowUpAt != null && nextFollowUpAt.isBefore(Instant.now());
    }
}
