package com.itilms.notification.repository;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.itilms.notification.entity.Announcement;
import com.itilms.notification.entity.Announcement.Audience;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    Page<Announcement> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    /** What one person sees: live announcements addressed to them, plus anything they wrote. */
    default Page<Announcement> visibleTo(Long userId, String role, Instant now, Pageable pageable) {
        return visibleTo(userId, role, now, Audience.ALL, Audience.ROLE, Audience.BATCH, Audience.COURSE, pageable);
    }

    @Query("""
            SELECT a FROM Announcement a
             WHERE a.createdBy = :userId
                OR (a.withdrawn = false AND (a.expiresAt IS NULL OR a.expiresAt > :now)
                    AND (a.audience = :all
                         OR (a.audience = :byRole AND a.targetRole = :role)
                         OR (a.audience = :byBatch AND a.targetId IN
                              (SELECT m.batchId FROM BatchMember m WHERE m.userId = :userId AND m.active = true))
                         OR (a.audience = :byCourse AND a.targetId IN
                              (SELECT m.courseId FROM BatchMember m WHERE m.userId = :userId AND m.active = true))))
             ORDER BY a.createdAt DESC, a.id DESC
            """)
    Page<Announcement> visibleTo(@Param("userId") Long userId, @Param("role") String role, @Param("now") Instant now,
                                 @Param("all") Audience all, @Param("byRole") Audience byRole,
                                 @Param("byBatch") Audience byBatch, @Param("byCourse") Audience byCourse,
                                 Pageable pageable);
}
