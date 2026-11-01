package com.itilms.certificate.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.itilms.certificate.entity.IdCardPhoto;

@Repository
public interface IdCardPhotoRepository extends JpaRepository<IdCardPhoto, Long> {
}
