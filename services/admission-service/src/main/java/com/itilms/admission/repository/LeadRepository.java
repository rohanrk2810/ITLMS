package com.itilms.admission.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.itilms.admission.entity.Lead;
import com.itilms.admission.entity.LeadStatus;

@Repository
public interface LeadRepository extends JpaRepository<Lead, Long>, JpaSpecificationExecutor<Lead> {

    List<Lead> findByPhone(String phone);

    Optional<Lead> findByConvertedStudentId(Long studentId);

    long countByStatus(LeadStatus status);

    /**
     * Follow-ups a counselor has let slip.
     *
     * <p>Doc S15 puts "attendance pending" on the coordinator dashboard; the
     * counselor equivalent is this. A lead with a follow-up date in the past
     * and nobody acting on it is the single clearest sign of admissions
     * revenue leaking away.
     */
    @Query("""
            SELECT l FROM Lead l
             WHERE l.status IN ('NEW','CONTACTED','FOLLOW_UP','INTERESTED')
               AND l.nextFollowUpAt IS NOT NULL
               AND l.nextFollowUpAt < :now
               AND (:counselorUserId IS NULL OR l.counselorUserId = :counselorUserId)
             ORDER BY l.nextFollowUpAt
            """)
    List<Lead> findOverdueFollowUps(@Param("now") Instant now,
                                    @Param("counselorUserId") Long counselorUserId);

    @Query("""
            SELECT l FROM Lead l
             WHERE l.status IN ('NEW','CONTACTED','FOLLOW_UP','INTERESTED')
               AND l.nextFollowUpAt BETWEEN :from AND :to
               AND (:counselorUserId IS NULL OR l.counselorUserId = :counselorUserId)
             ORDER BY l.nextFollowUpAt
            """)
    List<Lead> findFollowUpsDueBetween(@Param("from") Instant from,
                                       @Param("to") Instant to,
                                       @Param("counselorUserId") Long counselorUserId);

    /** Conversion funnel for the placement/admissions dashboard. */
    @Query("SELECT l.status, COUNT(l) FROM Lead l GROUP BY l.status")
    List<Object[]> countGroupedByStatus();

    @Query("SELECT l.source, COUNT(l) FROM Lead l WHERE l.status = 'CONVERTED' GROUP BY l.source")
    List<Object[]> countConversionsBySource();
}
