package com.itilms.file.entity;

import com.itilms.common.entity.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Metadata for one uploaded file. The bytes live in the storage backend
 * (local disk or MinIO); this row is the only record of what they are, whose
 * they are, and where to find them again.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "files")
public class FileObject extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Path inside the storage backend. Never handed to a client. */
    @Column(name = "storage_key", nullable = false, length = 300, unique = true)
    private String storageKey;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 150)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FileCategory category;

    /** Whose record this file belongs to; not always the uploader (staff may act on a student's behalf). */
    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "uploaded_by", nullable = false)
    private Long uploadedBy;
}
