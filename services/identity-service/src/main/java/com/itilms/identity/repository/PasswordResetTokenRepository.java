package com.itilms.identity.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.itilms.identity.entity.PasswordResetToken;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /**
     * Requesting a new reset link invalidates any earlier one. Without this a
     * user who requested three links would have three usable doors, each with
     * its own expiry, and closing one would not close the others.
     */
    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.usedAt = :now WHERE t.userId = :userId AND t.usedAt IS NULL")
    int invalidateAllForUser(@Param("userId") Long userId, @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);

    /** Rate-limit input: how many links this account asked for recently. */
    @Query("""
            SELECT COUNT(t) FROM PasswordResetToken t
            WHERE t.userId = :userId AND t.createdAt > :since
            """)
    long countRecentRequests(@Param("userId") Long userId, @Param("since") Instant since);
}
