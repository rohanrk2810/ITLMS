package com.itilms.identity.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.itilms.identity.entity.LoginOtp;

@Repository
public interface LoginOtpRepository extends JpaRepository<LoginOtp, Long> {

    /** The code a person can currently be typing in: their newest request that is not yet used. */
    Optional<LoginOtp> findFirstByPhoneDigitsAndUsedAtIsNullOrderByCreatedAtDesc(String phoneDigits);

    Optional<LoginOtp> findFirstByPhoneDigitsOrderByCreatedAtDesc(String phoneDigits);

    @Modifying
    @Query("UPDATE LoginOtp o SET o.usedAt = :now WHERE o.phoneDigits = :phone AND o.usedAt IS NULL")
    int invalidateAllForPhone(@Param("phone") String phoneDigits, @Param("now") Instant now);

    @Query("SELECT COUNT(o) FROM LoginOtp o WHERE o.phoneDigits = :phone AND o.createdAt > :since")
    long countRecent(@Param("phone") String phoneDigits, @Param("since") Instant since);

    @Modifying
    @Query("DELETE FROM LoginOtp o WHERE o.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
