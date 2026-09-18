package com.itilms.file.service;

import java.io.InputStream;

import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import com.itilms.common.dto.PageResponse;
import com.itilms.file.dto.FileResponse;
import com.itilms.file.entity.FileCategory;

public interface FileService {

    FileResponse upload(MultipartFile file, FileCategory category, Long ownerUserId);

    FileResponse get(Long id);

    Download download(Long id);

    void delete(Long id);

    PageResponse<FileResponse> list(Long ownerUserId, FileCategory category, Pageable pageable);

    record Download(InputStream content, String filename, String contentType, long sizeBytes) {
    }
}
