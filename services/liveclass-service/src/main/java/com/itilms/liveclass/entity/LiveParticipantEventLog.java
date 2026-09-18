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

/**
 * A single webhook delivery from LiveKit, stored verbatim in summary form.
 *
 * <p>Deliberately not an {@link com.itilms.common.entity.AuditableEntity}: these
 * rows record what the media server said, not what a user did, so there is no
 * acting user to attribute them to.
 *
 * <p>They exist because every attendance figure in this service is derived. When
 * a student disputes being marked absent, or the institute changes its
 * threshold, the answer comes from replaying these rows rather than from an
 * argument about what the totals used to say.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "live_participant_events")
public class LiveParticipantEventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "live_session_id", nullable = false)
    private Long liveSessionId;

    @Column(length = 80)
    private String identity;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "participant_sid", length = 80)
    private String participantSid;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    /** LiveKit's own id for the delivery; the unique key that makes replay a no-op. */
    @Column(name = "livekit_event_id", length = 80)
    private String livekitEventId;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
