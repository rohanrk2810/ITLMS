package com.itilms.file.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.InputStreamResource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.itilms.common.dto.PageResponse;
import com.itilms.common.security.Roles;
import com.itilms.file.dto.FileResponse;
import com.itilms.file.entity.FileCategory;
import com.itilms.file.service.FileService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** Upload, metadata and authorised download (Doc S12). */
@Tag(name = "Files", description = "Upload, metadata and controlled download")
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @Operation(summary = "Upload a file",
            description = "Who may upload depends on the category: an avatar is anyone's own, "
                    + "a submission is the student's own, course material is staff or a trainer. "
                    + "Staff (and finance, for receipts) may set ownerUserId to upload on someone else's behalf.")
    @PreAuthorize("isAuthenticated()")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileResponse> upload(@RequestParam("file") MultipartFile file,
                                               @RequestParam FileCategory category,
                                               @RequestParam(required = false) Long ownerUserId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(fileService.upload(file, category, ownerUserId));
    }

    @Operation(summary = "Files I uploaded", description = "Every file the caller uploaded themselves, most recent first.")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/mine")
    public PageResponse<FileResponse> listMine(@PageableDefault(size = 20) Pageable pageable) {
        return fileService.listMine(pageable);
    }

    @Operation(summary = "File metadata")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public FileResponse get(@PathVariable Long id) {
        return fileService.get(id);
    }

    @Operation(summary = "Download the file",
            description = "Same authorisation as the metadata endpoint. Streams the stored bytes back.")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long id) {
        FileService.Download download = fileService.download(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(download.filename()).build().toString())
                // Documents, submissions and receipts can name a person; never cache them on a shared proxy.
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentLength(download.sizeBytes())
                .contentType(MediaType.parseMediaType(download.contentType()))
                .body(new InputStreamResource(download.content()));
    }

    @Operation(summary = "Delete a file", description = "The uploader may remove their own mistake; otherwise staff only.")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        fileService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "List files", description = "Staff only, for support and audit.")
    @PreAuthorize(Roles.STAFF)
    @GetMapping
    public PageResponse<FileResponse> list(@RequestParam(required = false) Long ownerUserId,
                                           @RequestParam(required = false) FileCategory category,
                                           @PageableDefault(size = 20) Pageable pageable) {
        return fileService.list(ownerUserId, category, pageable);
    }
}
