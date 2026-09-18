package com.itilms.admission.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.itilms.admission.entity.DocumentType;
import com.itilms.admission.entity.StudentDocument;

@Repository
public interface StudentDocumentRepository extends JpaRepository<StudentDocument, Long> {

    List<StudentDocument> findByStudentIdOrderByUploadedAtDesc(Long studentId);

    Optional<StudentDocument> findByStudentIdAndDocumentType(Long studentId, DocumentType documentType);

    long countByStudentIdAndVerifiedTrue(Long studentId);
}
