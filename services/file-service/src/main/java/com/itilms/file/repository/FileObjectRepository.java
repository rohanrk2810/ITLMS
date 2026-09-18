package com.itilms.file.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.itilms.file.entity.FileCategory;
import com.itilms.file.entity.FileObject;

public interface FileObjectRepository extends JpaRepository<FileObject, Long> {

    Page<FileObject> findAllByOrderByIdDesc(Pageable pageable);

    Page<FileObject> findByOwnerUserIdOrderByIdDesc(Long ownerUserId, Pageable pageable);

    Page<FileObject> findByCategoryOrderByIdDesc(FileCategory category, Pageable pageable);

    Page<FileObject> findByOwnerUserIdAndCategoryOrderByIdDesc(
            Long ownerUserId, FileCategory category, Pageable pageable);
}
