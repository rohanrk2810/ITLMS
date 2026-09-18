package com.itilms.file.dto;

import java.time.Instant;

import com.itilms.file.entity.FileCategory;
import com.itilms.file.entity.FileObject;

public record FileResponse(
        Long id,
        String filename,
        String contentType,
        long sizeBytes,
        FileCategory category,
        Long ownerUserId,
        Instant uploadedAt,
        String downloadUrl
) {
    public static FileResponse from(FileObject f) {
        return new FileResponse(f.getId(), f.getOriginalFilename(), f.getContentType(), f.getSizeBytes(),
                f.getCategory(), f.getOwnerUserId(), f.getCreatedAt(), "/api/files/%d/download".formatted(f.getId()));
    }
}
