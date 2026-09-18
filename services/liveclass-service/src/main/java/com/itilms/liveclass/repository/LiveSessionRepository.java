package com.itilms.liveclass.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.liveclass.entity.LiveSession;
import com.itilms.liveclass.entity.LiveSessionStatus;

public interface LiveSessionRepository extends JpaRepository<LiveSession, Long> {

    /**
     * Loads a session and holds its row until the transaction ends.
     *
     * <p>Webhooks for one room arrive concurrently - forty students joining at
     * 10:00 is forty near-simultaneous deliveries - and the sweep job may reach
     * the same room at the moment LiveKit reports it finished. Serialising work
     * per room is what guarantees attendance is published exactly once and the
     * peak participant count is not lost to an interleaved write.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM LiveSession s WHERE s.id = :id")
    Optional<LiveSession> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM LiveSession s WHERE s.roomName = :roomName")
    Optional<LiveSession> findByRoomNameForUpdate(@Param("roomName") String roomName);

    /**
     * Records the LiveKit room sid without touching anything else on the row.
     *
     * <p>A targeted update rather than a load-and-save, because room creation
     * happens outside any lock: saving a whole entity loaded a second earlier
     * could overwrite the LIVE status a webhook has just written. Unconditional,
     * because a room LiveKit closed and recreated has a new sid.
     */
    @Modifying
    @Transactional
    @Query("UPDATE LiveSession s SET s.livekitRoomSid = :sid WHERE s.id = :id")
    int recordRoomSid(@Param("id") Long id, @Param("sid") String sid);

    Optional<LiveSession> findByClassSessionId(Long classSessionId);

    Optional<LiveSession> findByRoomName(String roomName);

    boolean existsByClassSessionId(Long classSessionId);

    List<LiveSession> findByBatchIdOrderByScheduledStartAtDesc(Long batchId);

    Page<LiveSession> findByStatusOrderByScheduledStartAtDesc(LiveSessionStatus status, Pageable pageable);

    Page<LiveSession> findAllByOrderByScheduledStartAtDesc(Pageable pageable);

    List<LiveSession> findBySessionDateOrderByStartTimeAsc(LocalDate date);

    /**
     * Rooms whose class has finished but whose attendance was never published.
     *
     * <p>The sweep's main query. Normally LiveKit reports the room finishing and
     * attendance follows from that; this catches the cases where it does not -
     * a lost final webhook, or a class nobody ever opened - so a register is
     * never left silently blank.
     */
    @Query("""
            SELECT s FROM LiveSession s
            WHERE s.attendanceComputed = false
              AND s.status <> com.itilms.liveclass.entity.LiveSessionStatus.CANCELLED
              AND s.scheduledEndAt < :cutoff
            ORDER BY s.scheduledEndAt ASC
            """)
    List<LiveSession> findUnsettledBefore(@Param("cutoff") Instant cutoff);

    /**
     * Rooms due to start shortly that have not been created on LiveKit yet.
     *
     * <p>Pre-creating them means the trainer is not waiting on a room-creation
     * round trip at the moment the class starts.
     */
    @Query("""
            SELECT s FROM LiveSession s
            WHERE s.livekitRoomSid IS NULL
              AND s.status = com.itilms.liveclass.entity.LiveSessionStatus.SCHEDULED
              AND s.scheduledStartAt BETWEEN :from AND :to
            ORDER BY s.scheduledStartAt ASC
            """)
    List<LiveSession> findDueForProvisioning(@Param("from") Instant from, @Param("to") Instant to);

    /** A trainer's live classes, for the trainer dashboard. */
    @Query("""
            SELECT s FROM LiveSession s
            WHERE s.trainerId = :trainerId
              AND s.scheduledEndAt >= :from
            ORDER BY s.scheduledStartAt ASC
            """)
    List<LiveSession> findUpcomingForTrainer(@Param("trainerId") Long trainerId,
                                             @Param("from") Instant from);

    /**
     * Live classes for a set of batches, for the student dashboard.
     *
     * <p>Takes the batch ids rather than a student id because this service has
     * no roster of its own: which batches a student is in is batch-service's
     * fact, and this query is fed by that answer.
     */
    @Query("""
            SELECT s FROM LiveSession s
            WHERE s.batchId IN :batchIds
              AND s.scheduledEndAt >= :from
              AND s.status <> com.itilms.liveclass.entity.LiveSessionStatus.CANCELLED
            ORDER BY s.scheduledStartAt ASC
            """)
    List<LiveSession> findUpcomingForBatches(@Param("batchIds") List<Long> batchIds,
                                             @Param("from") Instant from);
}
