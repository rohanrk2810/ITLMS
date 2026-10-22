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

/** One finished phrase of what the trainer said, with how far into the class it was said. Words only, never audio. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "live_transcript_lines")
public class LiveTranscriptLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "live_session_id", nullable = false)
    private Long liveSessionId;

    /** Position in the transcript, from 1. */
    @Column(nullable = false)
    private int seq;

    /** Seconds from the start of the class. */
    @Column(name = "at_seconds", nullable = false)
    private int atSeconds;

    @Column(length = 10)
    private String language;

    @Column(nullable = false, length = 1000)
    private String text;

    /** Made by the browser, so a retry that arrives twice is stored once. */
    @Column(name = "client_id", length = 40)
    private String clientId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
