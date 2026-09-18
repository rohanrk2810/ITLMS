package com.itilms.file.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.unit.DataSize;

import com.itilms.common.event.EventPublisher;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.ForbiddenOperationException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.common.security.AppPrincipal;
import com.itilms.file.config.FileProperties;
import com.itilms.file.dto.FileResponse;
import com.itilms.file.entity.FileCategory;
import com.itilms.file.entity.FileObject;
import com.itilms.file.exception.StorageException;
import com.itilms.file.repository.FileObjectRepository;
import com.itilms.file.service.FileService;
import com.itilms.file.storage.StorageBackend;

/** Upload validation, ownership and the read/delete authorisation that guard the storage layer. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FileServiceImplTest {

    @Mock private FileObjectRepository repository;
    @Mock private StorageBackend storage;
    @Mock private EventPublisher events;

    private FileServiceImpl service;

    private static final AppPrincipal STUDENT = new AppPrincipal(10L, "s@x", "Student", "STUDENT", 1L);
    private static final AppPrincipal OTHER_STUDENT = new AppPrincipal(11L, "s2@x", "Other", "STUDENT", 2L);
    private static final AppPrincipal TRAINER = new AppPrincipal(20L, "t@x", "Trainer", "TRAINER", 5L);
    private static final AppPrincipal ADMIN = new AppPrincipal(30L, "a@x", "Admin", "ADMIN", null);

    @BeforeEach
    void setUp() {
        FileProperties props = new FileProperties();
        props.setAllowedContentTypes(List.of("application/pdf", "image/png"));
        props.setMaxSizeByCategory(Map.of(FileCategory.SUBMISSION, DataSize.ofMegabytes(1)));
        service = new FileServiceImpl(repository, storage, props, events);
        when(repository.save(any())).thenAnswer(inv -> {
            FileObject f = inv.getArgument(0);
            f.setId(99L);
            return f;
        });
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void actAs(AppPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
    }

    private MockMultipartFile pdf(String content) {
        return new MockMultipartFile("file", "answer.pdf", "application/pdf", content.getBytes());
    }

    @Test
    @DisplayName("A student uploading their own submission is stored and returned")
    void uploadStoresOwnSubmission() throws Exception {
        actAs(STUDENT);
        FileResponse response = service.upload(pdf("hello"), FileCategory.SUBMISSION, null);

        assertThat(response.ownerUserId()).isEqualTo(STUDENT.userId());
        assertThat(response.category()).isEqualTo(FileCategory.SUBMISSION);
        verify(storage).store(any(), any(), org.mockito.ArgumentMatchers.eq(5L),
                org.mockito.ArgumentMatchers.eq("application/pdf"));
    }

    @Test
    @DisplayName("A trainer may not upload a submission - that role belongs to the student")
    void uploadRejectsWrongRoleForCategory() {
        actAs(TRAINER);
        assertThatThrownBy(() -> service.upload(pdf("x"), FileCategory.SUBMISSION, null))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    @DisplayName("A file type outside the allow-list is rejected before it reaches storage")
    void uploadRejectsUnsupportedType() {
        actAs(STUDENT);
        MockMultipartFile exe = new MockMultipartFile("file", "virus.exe", "application/x-msdownload", "x".getBytes());
        assertThatThrownBy(() -> service.upload(exe, FileCategory.SUBMISSION, null))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("A file over its category's size ceiling is rejected")
    void uploadRejectsOversizeFile() {
        actAs(STUDENT);
        byte[] big = new byte[(int) DataSize.ofMegabytes(2).toBytes()];
        MockMultipartFile huge = new MockMultipartFile("file", "big.pdf", "application/pdf", big);
        assertThatThrownBy(() -> service.upload(huge, FileCategory.SUBMISSION, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("exceed");
    }

    @Test
    @DisplayName("A student cannot upload a document claiming to be someone else")
    void uploadRejectsActingOnBehalfOfAnotherStudent() {
        actAs(STUDENT);
        assertThatThrownBy(() -> service.upload(pdf("x"), FileCategory.DOCUMENT, OTHER_STUDENT.userId()))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    @DisplayName("Staff may upload a document naming a student as the owner")
    void uploadAllowsStaffActingOnBehalfOfAStudent() throws Exception {
        actAs(ADMIN);
        FileResponse response = service.upload(pdf("x"), FileCategory.DOCUMENT, STUDENT.userId());
        assertThat(response.ownerUserId()).isEqualTo(STUDENT.userId());
    }

    @Test
    @DisplayName("A storage failure surfaces as a StorageException, not a silent loss")
    void uploadWrapsStorageFailure() throws Exception {
        actAs(STUDENT);
        org.mockito.Mockito.doThrow(new java.io.IOException("disk full"))
                .when(storage).store(any(), any(), org.mockito.ArgumentMatchers.anyLong(), any());
        assertThatThrownBy(() -> service.upload(pdf("x"), FileCategory.SUBMISSION, null))
                .isInstanceOf(StorageException.class);
    }

    private FileObject submissionOf(Long ownerUserId, Long uploadedBy) {
        return FileObject.builder().id(1L).storageKey("k").originalFilename("f.pdf")
                .contentType("application/pdf").sizeBytes(10L).category(FileCategory.SUBMISSION)
                .ownerUserId(ownerUserId).uploadedBy(uploadedBy).build();
    }

    @Test
    @DisplayName("The owning student can download their own submission")
    void downloadAllowsOwner() throws Exception {
        FileObject file = submissionOf(STUDENT.userId(), STUDENT.userId());
        when(repository.findById(1L)).thenReturn(Optional.of(file));
        when(storage.load("k")).thenReturn(new ByteArrayInputStream("data".getBytes()));
        actAs(STUDENT);

        FileService.Download download = service.download(1L);
        assertThat(download.filename()).isEqualTo("f.pdf");
    }

    @Test
    @DisplayName("A submission is closed to a student who did not make it")
    void downloadRejectsAStrangerStudent() {
        FileObject file = submissionOf(STUDENT.userId(), STUDENT.userId());
        when(repository.findById(1L)).thenReturn(Optional.of(file));
        actAs(OTHER_STUDENT);

        assertThatThrownBy(() -> service.download(1L)).isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    @DisplayName("An unknown file id is reported as not found")
    void getUnknownFileThrowsNotFound() {
        when(repository.findById(404L)).thenReturn(Optional.empty());
        actAs(STUDENT);
        assertThatThrownBy(() -> service.get(404L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("The uploader can delete their own file")
    void deleteAllowsUploader() throws Exception {
        FileObject file = submissionOf(STUDENT.userId(), STUDENT.userId());
        when(repository.findById(1L)).thenReturn(Optional.of(file));
        actAs(STUDENT);

        service.delete(1L);
        verify(storage).delete("k");
        verify(repository).delete(file);
        verify(events).audit(org.mockito.ArgumentMatchers.eq("file-service"),
                org.mockito.ArgumentMatchers.eq("FILE_DELETED"), any(), any(), any(), any());
    }

    @Test
    @DisplayName("A classmate cannot delete someone else's submission")
    void deleteRejectsNonUploaderNonStaff() {
        FileObject file = submissionOf(STUDENT.userId(), STUDENT.userId());
        when(repository.findById(1L)).thenReturn(Optional.of(file));
        actAs(OTHER_STUDENT);

        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    @DisplayName("Listing filters by owner and category when both are given")
    void listUsesTheMostSpecificFinder() {
        actAs(ADMIN);
        when(repository.findByOwnerUserIdAndCategoryOrderByIdDesc(any(), any(), any())).thenReturn(Page.empty());
        service.list(STUDENT.userId(), FileCategory.SUBMISSION, PageRequest.of(0, 20));
        verify(repository).findByOwnerUserIdAndCategoryOrderByIdDesc(
                STUDENT.userId(), FileCategory.SUBMISSION, PageRequest.of(0, 20));
    }
}
