package com.itilms.notification.entity;

import java.time.Duration;
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

/** An email queued for sending. Never holds a password or a reset link. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "email_outbox")
public class EmailOutbox {

    public static final String PENDING = "PENDING";
    public static final String SENT = "SENT";
    public static final String FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "to_address", nullable = false, length = 160)
    private String toAddress;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(name = "text_body", nullable = false, columnDefinition = "text")
    private String textBody;

    @Column(name = "html_body", nullable = false, columnDefinition = "text")
    private String htmlBody;

    @Builder.Default
    @Column(nullable = false, length = 10)
    private String status = PENDING;

    @Column(nullable = false)
    private int attempts;

    @Builder.Default
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt = Instant.now();

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public void markSent(Instant at) {
        attempts++;
        status = SENT;
        sentAt = at;
        lastError = null;
    }

    /**
     * Records a failed attempt. The wait doubles each time - 2, 4, 8, 16
     * minutes - so a mail server that is down for an hour is not hammered,
     * and one that is down for good is given up on within the hour.
     */
    public void markFailed(String error, Instant at, int maxAttempts) {
        attempts++;
        lastError = error == null ? null : error.substring(0, Math.min(error.length(), 500));
        if (attempts >= maxAttempts) {
            status = FAILED;
        } else {
            nextAttemptAt = at.plus(Duration.ofMinutes(1L << attempts));
        }
    }
}
