package com.itilms.notification.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.itilms.notification.entity.Recipient;

public interface RecipientRepository extends JpaRepository<Recipient, Long> {

    /** Name, email and role changed. Leaves the active flag to status events. */
    @Modifying
    @Query(value = """
            INSERT INTO recipients (user_id, email, full_name, role, active, updated_at)
            VALUES (:userId, :email, :fullName, :role, TRUE, NOW())
            ON CONFLICT (user_id) DO UPDATE
               SET email = EXCLUDED.email, full_name = EXCLUDED.full_name,
                   role = EXCLUDED.role, updated_at = NOW()
            """, nativeQuery = true)
    void upsertProfile(@Param("userId") Long userId, @Param("email") String email,
                       @Param("fullName") String fullName, @Param("role") String role);

    /** A status change, applied only if it is newer than the last one seen. */
    @Modifying
    @Query(value = """
            INSERT INTO recipients (user_id, email, active, status_changed_at, updated_at)
            VALUES (:userId, :email, :active, :at, NOW())
            ON CONFLICT (user_id) DO UPDATE
               SET active = EXCLUDED.active, status_changed_at = EXCLUDED.status_changed_at, updated_at = NOW()
             WHERE recipients.status_changed_at IS NULL
                OR recipients.status_changed_at <= EXCLUDED.status_changed_at
            """, nativeQuery = true)
    void upsertStatus(@Param("userId") Long userId, @Param("email") String email,
                      @Param("active") boolean active, @Param("at") Instant at);

    /** Learns about someone from their own sign-in, without overwriting anything known. */
    @Modifying
    @Query(value = """
            INSERT INTO recipients (user_id, email, full_name, role, active, updated_at)
            VALUES (:userId, :email, :fullName, :role, TRUE, NOW())
            ON CONFLICT (user_id) DO NOTHING
            """, nativeQuery = true)
    void insertIfAbsent(@Param("userId") Long userId, @Param("email") String email,
                        @Param("fullName") String fullName, @Param("role") String role);

    @Query("SELECT r.userId FROM Recipient r WHERE r.active = true AND r.role = :role")
    List<Long> activeUserIdsWithRole(@Param("role") String role);

    @Query("SELECT r.userId FROM Recipient r WHERE r.active = true")
    List<Long> activeUserIds();
}
