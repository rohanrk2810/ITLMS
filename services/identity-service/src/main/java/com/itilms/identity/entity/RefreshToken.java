package com.itilms.identity.entity;

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
 * A long-lived credential that can be exchanged for a fresh access token.
 *
 * <p>Access tokens are short-lived and cannot be revoked once issued — that is
 * the trade for making them verifiable without a database hit. The refresh
 * token is the counterweight: it <em>is</em> stored, so signing a user out,
 * blocking an account, or reacting to a stolen device takes effect within one
 * access-token lifetime rather than never.
 *
 * <p>Refresh is rotating: using a token revokes it and issues a new one, with
 * {@code replacedBy} recording the chain. If a revoked token is presented
 * again, the only sensible explanation is that it was copied, and the service
 * revokes the entire family rather than guessing which holder is legitimate.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** SHA-256 of the token that was handed to the client. */
    @Column(name = "token_hash", nullable = false, length = 100)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by", length = 100)
    private String replacedBy;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    public boolean isActive() {
        return revokedAt == null && expiresAt.isAfter(Instant.now());
    }

    public void revoke() {
        if (revokedAt == null) {
            revokedAt = Instant.now();
        }
    }
}
