package com.itilms.identity.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.itilms.identity.entity.User;
import com.itilms.identity.entity.UserRole;
import com.itilms.identity.entity.UserStatus;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByPhone(String phone);

    /**
     * Doc S6.1: sign-in accepts email or phone. One query rather than two
     * lookups, so the response time does not depend on which the user typed.
     */
    @Query("""
            SELECT u FROM User u
            WHERE lower(u.email) = lower(:identifier)
               OR u.phone = :identifier
            """)
    Optional<User> findByEmailOrPhone(@Param("identifier") String identifier);

    /** Accounts whose phone ends in these 10 digits, whatever punctuation or country code it was typed with. */
    @Query(value = """
            SELECT * FROM users
            WHERE phone IS NOT NULL
              AND right(regexp_replace(phone, '[^0-9]', '', 'g'), 10) = :last10
            """, nativeQuery = true)
    java.util.List<User> findAllByPhoneEndingWith(@Param("last10") String last10);

    /** Everyone who is not a student and can still sign in: the staff the finance desk pays. */
    List<User> findByRoleNotAndStatusOrderByFirstNameAscLastNameAsc(UserRole role, UserStatus status);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByPhone(String phone);

    /** True when some <em>other</em> account already holds this email. */
    @Query("SELECT COUNT(u) > 0 FROM User u WHERE lower(u.email) = lower(:email) AND u.id <> :excludeId")
    boolean existsByEmailExcluding(@Param("email") String email, @Param("excludeId") Long excludeId);

    @Query("SELECT COUNT(u) > 0 FROM User u WHERE u.phone = :phone AND u.id <> :excludeId")
    boolean existsByPhoneExcluding(@Param("phone") String phone, @Param("excludeId") Long excludeId);

    /**
     * Bulk lookup used by other services to turn user ids into display names.
     * Notification and reporting resolve dozens at a time; N+1 single fetches
     * over HTTP would be far worse than one query.
     */
    List<User> findByIdIn(Collection<Long> ids);

    List<User> findByRoleAndStatus(UserRole role, UserStatus status);

    long countByRoleAndStatus(UserRole role, UserStatus status);

    long countByStatus(UserStatus status);

    /**
     * Applies a {@code ProfileLinkedEvent}. Written as an update rather than a
     * read-modify-save so it cannot clobber a concurrent profile edit, and so
     * the consumer stays cheap when replaying a backlog.
     */
    @Modifying
    @Query("UPDATE User u SET u.profileId = :profileId, u.profileCode = :profileCode WHERE u.id = :userId")
    int linkProfile(@Param("userId") Long userId,
                    @Param("profileId") Long profileId,
                    @Param("profileCode") String profileCode);

    @Query("SELECT u.id FROM User u WHERE u.role = :role AND u.status = 'ACTIVE'")
    List<Long> findActiveUserIdsByRole(@Param("role") UserRole role);
}
