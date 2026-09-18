package com.itilms.notification.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.itilms.notification.entity.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * Writes the notification unless this event already reached this person.
     *
     * @return 1 if written, 0 if it was a repeat - the caller emails only on 1,
     *         so a redelivered event does not send a second email either
     */
    @Modifying
    @Query(value = """
            INSERT INTO notifications (source_event_id, user_id, type, title, message, action_url)
            VALUES (:eventId, :userId, :type, :title, :message, :actionUrl)
            ON CONFLICT (source_event_id, user_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("eventId") String eventId, @Param("userId") Long userId,
                       @Param("type") String type, @Param("title") String title,
                       @Param("message") String message, @Param("actionUrl") String actionUrl);

    Page<Notification> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    Page<Notification> findByUserIdAndReadAtIsNullOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    long countByUserIdAndReadAtIsNull(Long userId);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Notification n SET n.readAt = :at WHERE n.id = :id AND n.userId = :userId AND n.readAt IS NULL")
    int markRead(@Param("id") Long id, @Param("userId") Long userId, @Param("at") Instant at);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Notification n SET n.readAt = :at WHERE n.userId = :userId AND n.readAt IS NULL")
    int markAllRead(@Param("userId") Long userId, @Param("at") Instant at);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.sourceEventId = :eventId")
    int deleteBySourceEventId(@Param("eventId") String eventId);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.createdAt < :cutoff")
    int deleteCreatedBefore(@Param("cutoff") Instant cutoff);
}
