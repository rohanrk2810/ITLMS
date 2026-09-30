package com.itilms.certificate.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.itilms.certificate.entity.CertificateRequest;
import com.itilms.certificate.entity.CertificateRequestStatus;

public interface CertificateRequestRepository extends JpaRepository<CertificateRequest, Long> {

    List<CertificateRequest> findByStudentIdOrderByRequestedAtDesc(Long studentId);

    boolean existsByStudentIdAndCourseIdAndStatusIn(Long studentId, Long courseId,
                                                    Collection<CertificateRequestStatus> statuses);

    Optional<CertificateRequest> findFirstByStudentIdAndCourseIdAndStatusIn(Long studentId, Long courseId,
                                                                            Collection<CertificateRequestStatus> statuses);

    /**
     * Filters use sentinel values (0 for "any", the full status set, an open date range, a bare
     * percent sign) instead of IS NULL tests: a null parameter of unknown type makes Postgres
     * refuse the query.
     */
    @Query("""
            SELECT r FROM CertificateRequest r
            WHERE r.status IN :statuses
              AND (:courseId = 0 OR r.courseId = :courseId)
              AND (:batchId = 0 OR r.batchId = :batchId)
              AND r.requestedAt >= :from AND r.requestedAt < :to
              AND (LOWER(r.studentName) LIKE :q OR LOWER(COALESCE(r.studentCode, '')) LIKE :q)
            ORDER BY r.requestedAt DESC
            """)
    Page<CertificateRequest> search(@Param("statuses") Collection<CertificateRequestStatus> statuses,
                                    @Param("courseId") long courseId, @Param("batchId") long batchId,
                                    @Param("from") Instant from, @Param("to") Instant to,
                                    @Param("q") String q, Pageable pageable);
}
