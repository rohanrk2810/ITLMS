package com.itilms.certificate.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.itilms.certificate.entity.Certificate;
import com.itilms.certificate.entity.CertificateStatus;

public interface CertificateRepository extends JpaRepository<Certificate, Long> {

    Optional<Certificate> findByCertificateNo(String certificateNo);

    List<Certificate> findByStudentIdOrderByIssueDateDesc(Long studentId);

    boolean existsByStudentIdAndCourseIdAndStatus(Long studentId, Long courseId, CertificateStatus status);

    Page<Certificate> findByCourseIdOrderByIssueDateDesc(Long courseId, Pageable pageable);

    Page<Certificate> findAllByOrderByIssueDateDesc(Pageable pageable);

    /** From a database sequence, so two certificates issued at once never share a number. */
    @Query(value = "SELECT nextval('certificate_seq')", nativeQuery = true)
    long nextSequence();
}
