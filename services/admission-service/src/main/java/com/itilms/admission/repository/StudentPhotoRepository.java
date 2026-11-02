package com.itilms.admission.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.itilms.admission.entity.StudentPhoto;

@Repository
public interface StudentPhotoRepository extends JpaRepository<StudentPhoto, Long> {
}
