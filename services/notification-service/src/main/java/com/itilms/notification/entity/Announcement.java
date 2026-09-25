package com.itilms.notification.entity;

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

/** A message from the institute to a group of people (Doc S16). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "announcements")
public class Announcement extends AuditableEntity {

    /** Who an announcement reaches. */
    public enum Audience {
        ALL, ROLE, BATCH, COURSE
    }

    /** What an announcement is about, so a student can tell a test notice from a fee reminder. Independent of who it reaches. */
    public enum Category {
        GENERAL, COURSE, BATCH, LIVE_CLASS, TEST, ASSIGNMENT, INSTITUTE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Audience audience;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Category category = Category.GENERAL;

    @Column(name = "target_role", length = 20)
    private String targetRole;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "send_email", nullable = false)
    private boolean sendEmail;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean withdrawn;

    @Column(name = "recipient_count", nullable = false)
    private int recipientCount;

    /** The event id its notifications are filed under, so withdrawing can find them. */
    public String sourceEventId() {
        return "announcement-" + id;
    }

    public boolean isVisibleAt(Instant now) {
        return !withdrawn && (expiresAt == null || expiresAt.isAfter(now));
    }
}
