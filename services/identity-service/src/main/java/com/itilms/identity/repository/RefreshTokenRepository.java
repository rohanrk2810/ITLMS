package com.itilms.identity.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.itilms.identity.entity.RefreshToken;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByUserIdAndRevokedAtIsNull(Long userId);

    /**
     * Ends every session for a user at once — used on password change, on an
     * account being blocked, and when a reused refresh token suggests theft.
     */
    @Modifying
    @Query("UPDATE RefreshToken t SET t.revokedAt = :now WHERE t.userId = :userId AND t.revokedAt IS NULL")
    int revokeAllForUser(@Param("userId") Long userId, @Param("now") Instant now);

    /** Ends every session, recording why - used when a newer login replaces them. */
    @Modifying
    @Query("UPDATE RefreshToken t SET t.revokedAt = :now, t.revokeReason = :reason "
            + "WHERE t.userId = :userId AND t.revokedAt IS NULL")
    int revokeAllForUser(@Param("userId") Long userId, @Param("now") Instant now,
                         @Param("reason") String reason);

    /** Sessions still usable, newest activity first. */
    @Query("SELECT t FROM RefreshToken t WHERE t.userId = :userId AND t.revokedAt IS NULL "
            + "AND t.expiresAt > :now ORDER BY t.lastActivityAt DESC")
    List<RefreshToken> findLiveSessions(@Param("userId") Long userId, @Param("now") Instant now);

    /** Login history: one row per session (the tip of each rotation chain), newest first. */
    List<RefreshToken> findTop20ByUserIdAndReplacedByIsNullOrderBySessionStartedAtDesc(Long userId);

    /**
     * Housekeeping. Expired rows are worthless but accumulate quickly — one
     * per login per device — and slow the token-hash lookup on every refresh.
     */
    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);

    long countByUserIdAndRevokedAtIsNull(Long userId);
}
