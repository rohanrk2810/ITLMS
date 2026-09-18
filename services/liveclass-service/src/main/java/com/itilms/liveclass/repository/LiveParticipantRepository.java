package com.itilms.liveclass.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.liveclass.entity.LiveParticipant;

public interface LiveParticipantRepository extends JpaRepository<LiveParticipant, Long> {

    /**
     * Registers someone for a room, doing nothing if they already are.
     *
     * <p>A student double-clicking Join, or opening the class on a phone and a
     * laptop, sends two join requests at once. Check-then-insert would let both
     * pass the check and fail the second on the unique key; letting the database
     * resolve the race makes the second request a harmless no-op.
     *
     * @return 1 if a row was created, 0 if one already existed
     */
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO live_participants
                (live_session_id, identity, user_id, student_id, display_name, role,
                 attended_seconds, join_count, created_at, updated_at, created_by, updated_by)
            VALUES
                (:liveSessionId, :identity, :userId, :studentId, :displayName, :role,
                 0, 0, NOW(), NOW(), :userId, :userId)
            ON CONFLICT (live_session_id, identity) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("liveSessionId") Long liveSessionId,
                       @Param("identity") String identity,
                       @Param("userId") Long userId,
                       @Param("studentId") Long studentId,
                       @Param("displayName") String displayName,
                       @Param("role") String role);

    Optional<LiveParticipant> findByLiveSessionIdAndIdentity(Long liveSessionId, String identity);

    Optional<LiveParticipant> findByLiveSessionIdAndUserId(Long liveSessionId, Long userId);

    List<LiveParticipant> findByLiveSessionIdOrderByDisplayNameAsc(Long liveSessionId);

    /** Everyone still shown as in the room, which is what the sweep has to close out. */
    List<LiveParticipant> findByLiveSessionIdAndCurrentJoinAtIsNotNull(Long liveSessionId);

    long countByLiveSessionIdAndCurrentJoinAtIsNotNull(Long liveSessionId);

    /**
     * A student's live attendance across a batch.
     *
     * <p>Used by the student's own "how am I doing" view. The register itself
     * lives in batch-service; this is the underlying room time it was derived
     * from, which is what a student asks to see when they disagree with it.
     */
    @Query("""
            SELECT p FROM LiveParticipant p
            WHERE p.studentId = :studentId
              AND p.liveSessionId IN (
                    SELECT s.id FROM LiveSession s WHERE s.batchId = :batchId
              )
            ORDER BY p.liveSessionId DESC
            """)
    List<LiveParticipant> findForStudentInBatch(@Param("studentId") Long studentId,
                                                @Param("batchId") Long batchId);
}
