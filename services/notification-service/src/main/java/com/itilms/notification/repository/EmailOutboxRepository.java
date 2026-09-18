package com.itilms.notification.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.itilms.notification.entity.EmailOutbox;

public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, Long> {

    /**
     * Takes the next due emails and holds them until the transaction ends.
     * SKIP LOCKED lets a second instance of this service take a different set
     * instead of waiting - and instead of sending the same emails twice.
     */
    @Query(value = """
            SELECT * FROM email_outbox
             WHERE status = 'PENDING' AND next_attempt_at <= :now
             ORDER BY next_attempt_at
             LIMIT :limit
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<EmailOutbox> claimDue(@Param("now") Instant now, @Param("limit") int limit);

    @Modifying
    @Query("DELETE FROM EmailOutbox e WHERE e.createdAt < :cutoff AND e.status <> 'PENDING'")
    int deleteFinishedBefore(@Param("cutoff") Instant cutoff);
}
