package com.itilms.notification.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.itilms.notification.entity.BatchMember;

public interface BatchMemberRepository extends JpaRepository<BatchMember, BatchMember.Key> {

    /** Joined or left, applied only if newer than what is recorded. */
    @Modifying
    @Query(value = """
            INSERT INTO batch_members (batch_id, user_id, course_id, active, changed_at)
            VALUES (:batchId, :userId, :courseId, :active, :at)
            ON CONFLICT (batch_id, user_id) DO UPDATE
               SET course_id = EXCLUDED.course_id, active = EXCLUDED.active, changed_at = EXCLUDED.changed_at
             WHERE batch_members.changed_at <= EXCLUDED.changed_at
            """, nativeQuery = true)
    void upsert(@Param("batchId") Long batchId, @Param("userId") Long userId, @Param("courseId") Long courseId,
                @Param("active") boolean active, @Param("at") Instant at);

    // A deactivated account is skipped: nobody can sign in to read it.

    @Query("""
            SELECT DISTINCT m.userId FROM BatchMember m
             WHERE m.batchId = :batchId AND m.active = true
               AND NOT EXISTS (SELECT 1 FROM Recipient r WHERE r.userId = m.userId AND r.active = false)
            """)
    List<Long> currentMembersOfBatch(@Param("batchId") Long batchId);

    @Query("""
            SELECT DISTINCT m.userId FROM BatchMember m
             WHERE m.courseId = :courseId AND m.active = true
               AND NOT EXISTS (SELECT 1 FROM Recipient r WHERE r.userId = m.userId AND r.active = false)
            """)
    List<Long> currentMembersOfCourse(@Param("courseId") Long courseId);

    /** Current and former students of these courses: a job opening concerns graduates most of all. */
    @Query("""
            SELECT DISTINCT m.userId FROM BatchMember m
             WHERE m.courseId IN :courseIds
               AND NOT EXISTS (SELECT 1 FROM Recipient r WHERE r.userId = m.userId AND r.active = false)
            """)
    List<Long> everMembersOfCourses(@Param("courseIds") Collection<Long> courseIds);

    boolean existsByBatchIdAndUserIdAndActiveTrue(Long batchId, Long userId);

    boolean existsByCourseIdAndUserIdAndActiveTrue(Long courseId, Long userId);
}
