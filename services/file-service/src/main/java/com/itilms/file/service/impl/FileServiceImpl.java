package com.itilms.file.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.itilms.common.dto.PageResponse;
import com.itilms.common.event.EventPublisher;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.ForbiddenOperationException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.common.security.AppPrincipal;
import com.itilms.common.security.SecurityUtils;
import com.itilms.file.config.FileProperties;
import com.itilms.file.dto.FileResponse;
import com.itilms.file.entity.FileCategory;
import com.itilms.file.entity.FileObject;
import com.itilms.file.exception.StorageException;
import com.itilms.file.repository.FileObjectRepository;
import com.itilms.file.service.FileAccessRules;
import com.itilms.file.service.FileService;
import com.itilms.file.service.StorageKeys;
import com.itilms.file.service.UploadValidator;
import com.itilms.file.storage.StorageBackend;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private static final String SERVICE_NAME = "file-service";

    private final FileObjectRepository repository;
    private final StorageBackend storage;
    private final FileProperties props;
    private final EventPublisher events;

    @Override
    @Transactional
    public FileResponse upload(MultipartFile file, FileCategory category, Long ownerUserId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("EMPTY_FILE", "Choose a file to upload.");
        }
        if (category == null) {
            throw new BusinessRuleException("Choose which kind of file this is.");
        }
        AppPrincipal caller = SecurityUtils.requirePrincipal();
        if (!FileAccessRules.canUpload(caller, category)) {
            throw new ForbiddenOperationException("Your role may not upload " + category + " files.");
        }
        Long owner = resolveOwner(caller, category, ownerUserId);

        String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        UploadValidator.validate(props, contentType, file.getSize(), category);

        String key = StorageKeys.generate(category, file.getOriginalFilename());
        try (InputStream in = file.getInputStream()) {
            storage.store(key, in, file.getSize(), contentType);
        } catch (IOException ex) {
            throw new StorageException("Could not save the uploaded file", ex);
        }

        FileObject saved = repository.save(FileObject.builder()
                .storageKey(key)
                .originalFilename(originalName(file))
                .contentType(contentType)
                .sizeBytes(file.getSize())
                .category(category)
                .ownerUserId(owner)
                .uploadedBy(caller.userId())
                .build());

        log.info("Stored {} file {} ({} bytes) for owner {}", category, saved.getId(), saved.getSizeBytes(), owner);
        return FileResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public FileResponse get(Long id) {
        FileObject f = require(id);
        requireRead(f);
        return FileResponse.from(f);
    }

    @Override
    @Transactional(readOnly = true)
    public Download download(Long id) {
        FileObject f = require(id);
        requireRead(f);
        InputStream content;
        try {
            content = storage.load(f.getStorageKey());
        } catch (IOException ex) {
            throw new StorageException("Could not read the stored file", ex);
        }
        return new Download(content, f.getOriginalFilename(), f.getContentType(), f.getSizeBytes());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        AppPrincipal caller = SecurityUtils.requirePrincipal();
        FileObject f = require(id);
        if (!FileAccessRules.canDelete(caller, f.getUploadedBy())) {
            throw new ForbiddenOperationException("You may not delete this file.");
        }
        try {
            storage.delete(f.getStorageKey());
        } catch (IOException ex) {
            throw new StorageException("Could not remove the stored file", ex);
        }
        repository.delete(f);
        events.audit(SERVICE_NAME, "FILE_DELETED", "File", id,
                Map.of("category", f.getCategory(), "ownerUserId", f.getOwnerUserId()), null);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<FileResponse> list(Long ownerUserId, FileCategory category, Pageable pageable) {
        Page<FileObject> page;
        if (ownerUserId != null && category != null) {
            page = repository.findByOwnerUserIdAndCategoryOrderByIdDesc(ownerUserId, category, pageable);
        } else if (ownerUserId != null) {
            page = repository.findByOwnerUserIdOrderByIdDesc(ownerUserId, pageable);
        } else if (category != null) {
            page = repository.findByCategoryOrderByIdDesc(category, pageable);
        } else {
            page = repository.findAllByOrderByIdDesc(pageable);
        }
        return PageResponse.from(page, FileResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<FileResponse> listMine(Pageable pageable) {
        AppPrincipal caller = SecurityUtils.requirePrincipal();
        Page<FileObject> page = repository.findByUploadedByOrderByIdDesc(caller.userId(), pageable);
        return PageResponse.from(page, FileResponse::from);
    }

    // -----------------------------------------------------------------

    private Long resolveOwner(AppPrincipal caller, FileCategory category, Long requestedOwner) {
        if (requestedOwner == null || requestedOwner.equals(caller.userId())) {
            return caller.userId();
        }
        if (!FileAccessRules.canActOnBehalfOf(caller, category)) {
            throw new ForbiddenOperationException("You may only upload files for yourself.");
        }
        return requestedOwner;
    }

    private void requireRead(FileObject f) {
        AppPrincipal caller = SecurityUtils.requirePrincipal();
        if (!FileAccessRules.canRead(caller, f.getCategory(), f.getOwnerUserId())) {
            throw new ForbiddenOperationException("You may not access this file.");
        }
    }

    private FileObject require(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("File", id));
    }

    private String originalName(MultipartFile file) {
        String name = file.getOriginalFilename();
        return (name == null || name.isBlank()) ? "file" : name;
    }
}
