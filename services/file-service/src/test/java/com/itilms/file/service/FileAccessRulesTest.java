package com.itilms.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.itilms.common.security.AppPrincipal;
import com.itilms.file.entity.FileCategory;

/** Who may upload, read and delete a file, category by category. */
class FileAccessRulesTest {

    private static final AppPrincipal STUDENT = new AppPrincipal(1L, "s@x", "Student", "STUDENT", 10L);
    private static final AppPrincipal OTHER_STUDENT = new AppPrincipal(2L, "s2@x", "Other", "STUDENT", 11L);
    private static final AppPrincipal TRAINER = new AppPrincipal(3L, "t@x", "Trainer", "TRAINER", 20L);
    private static final AppPrincipal ADMIN = new AppPrincipal(4L, "a@x", "Admin", "ADMIN", null);
    private static final AppPrincipal COORDINATOR = new AppPrincipal(5L, "c@x", "Coordinator", "COORDINATOR", null);
    private static final AppPrincipal FINANCE = new AppPrincipal(6L, "f@x", "Finance", "FINANCE", null);
    private static final AppPrincipal PLACEMENT = new AppPrincipal(7L, "p@x", "Placement", "PLACEMENT", null);

    @Test
    @DisplayName("Anyone signed in may upload their own avatar")
    void avatarUploadIsOpen() {
        for (AppPrincipal caller : new AppPrincipal[] {STUDENT, TRAINER, ADMIN, FINANCE, PLACEMENT}) {
            assertThat(FileAccessRules.canUpload(caller, FileCategory.AVATAR)).isTrue();
        }
    }

    @Test
    @DisplayName("Only a student uploads a submission attachment")
    void submissionUploadIsStudentOnly() {
        assertThat(FileAccessRules.canUpload(STUDENT, FileCategory.SUBMISSION)).isTrue();
        assertThat(FileAccessRules.canUpload(TRAINER, FileCategory.SUBMISSION)).isFalse();
        assertThat(FileAccessRules.canUpload(ADMIN, FileCategory.SUBMISSION)).isFalse();
    }

    @Test
    @DisplayName("Course material comes from staff or a trainer, not a student")
    void lessonResourceUploadIsAcademic() {
        assertThat(FileAccessRules.canUpload(TRAINER, FileCategory.LESSON_RESOURCE)).isTrue();
        assertThat(FileAccessRules.canUpload(COORDINATOR, FileCategory.ASSIGNMENT)).isTrue();
        assertThat(FileAccessRules.canUpload(STUDENT, FileCategory.LESSON_RESOURCE)).isFalse();
    }

    @Test
    @DisplayName("A receipt is uploaded by finance or an admin, never placement")
    void receiptUploadIsFinanceDesk() {
        assertThat(FileAccessRules.canUpload(FINANCE, FileCategory.RECEIPT)).isTrue();
        assertThat(FileAccessRules.canUpload(ADMIN, FileCategory.RECEIPT)).isTrue();
        assertThat(FileAccessRules.canUpload(PLACEMENT, FileCategory.RECEIPT)).isFalse();
    }

    @Test
    @DisplayName("Only a student uploads a resume; not placement, not staff")
    void resumeUploadIsStudentOnly() {
        assertThat(FileAccessRules.canUpload(STUDENT, FileCategory.RESUME)).isTrue();
        assertThat(FileAccessRules.canUpload(PLACEMENT, FileCategory.RESUME)).isFalse();
        assertThat(FileAccessRules.canUpload(ADMIN, FileCategory.RESUME)).isFalse();
    }

    @Test
    @DisplayName("A resume is read by its owner, staff and the placement desk; not a classmate or a trainer")
    void resumeReadIsOwnerStaffOrPlacement() {
        assertThat(FileAccessRules.canRead(STUDENT, FileCategory.RESUME, STUDENT.userId())).isTrue();
        assertThat(FileAccessRules.canRead(ADMIN, FileCategory.RESUME, STUDENT.userId())).isTrue();
        assertThat(FileAccessRules.canRead(PLACEMENT, FileCategory.RESUME, STUDENT.userId())).isTrue();
        assertThat(FileAccessRules.canRead(OTHER_STUDENT, FileCategory.RESUME, STUDENT.userId())).isFalse();
        assertThat(FileAccessRules.canRead(TRAINER, FileCategory.RESUME, STUDENT.userId())).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = FileCategory.class, names = {"AVATAR", "ASSIGNMENT", "LESSON_RESOURCE"})
    @DisplayName("Course content and avatars are readable by anyone signed in, owner or not")
    void openlyReadableCategories(FileCategory category) {
        assertThat(FileAccessRules.canRead(OTHER_STUDENT, category, STUDENT.userId())).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = FileCategory.class, names = {"AVATAR", "ASSIGNMENT", "LESSON_RESOURCE"}, mode = EXCLUDE)
    @DisplayName("A personal file is closed to a student who does not own it")
    void personalCategoriesAreClosedToOtherStudents(FileCategory category) {
        assertThat(FileAccessRules.canRead(OTHER_STUDENT, category, STUDENT.userId())).isFalse();
        assertThat(FileAccessRules.canRead(STUDENT, category, STUDENT.userId())).isTrue();
    }

    @Test
    @DisplayName("Staff reads any document or certificate; a trainer does not")
    void staffReadsDocumentsAndCertificates() {
        assertThat(FileAccessRules.canRead(ADMIN, FileCategory.DOCUMENT, STUDENT.userId())).isTrue();
        assertThat(FileAccessRules.canRead(TRAINER, FileCategory.DOCUMENT, STUDENT.userId())).isFalse();
    }

    @Test
    @DisplayName("A trainer reads a student's submission; placement staff does not")
    void trainerReadsSubmissions() {
        assertThat(FileAccessRules.canRead(TRAINER, FileCategory.SUBMISSION, STUDENT.userId())).isTrue();
        assertThat(FileAccessRules.canRead(PLACEMENT, FileCategory.SUBMISSION, STUDENT.userId())).isFalse();
    }

    @Test
    @DisplayName("Finance and staff read a receipt that is not theirs (Roles.FINANCE_VIEW); placement does not")
    void receiptReadIsFinanceDesk() {
        assertThat(FileAccessRules.canRead(FINANCE, FileCategory.RECEIPT, STUDENT.userId())).isTrue();
        assertThat(FileAccessRules.canRead(COORDINATOR, FileCategory.RECEIPT, STUDENT.userId())).isTrue();
        assertThat(FileAccessRules.canRead(PLACEMENT, FileCategory.RECEIPT, STUDENT.userId())).isFalse();
    }

    @Test
    @DisplayName("The uploader deletes their own mistake; a stranger cannot")
    void deleteIsUploaderOrStaff() {
        assertThat(FileAccessRules.canDelete(STUDENT, STUDENT.userId())).isTrue();
        assertThat(FileAccessRules.canDelete(OTHER_STUDENT, STUDENT.userId())).isFalse();
        assertThat(FileAccessRules.canDelete(ADMIN, STUDENT.userId())).isTrue();
    }

    @Test
    @DisplayName("Staff may upload a document on a student's behalf; a trainer may not")
    void actOnBehalfOfIsStaffOrFinanceForReceipts() {
        assertThat(FileAccessRules.canActOnBehalfOf(ADMIN, FileCategory.DOCUMENT)).isTrue();
        assertThat(FileAccessRules.canActOnBehalfOf(TRAINER, FileCategory.DOCUMENT)).isFalse();
        assertThat(FileAccessRules.canActOnBehalfOf(FINANCE, FileCategory.RECEIPT)).isTrue();
        assertThat(FileAccessRules.canActOnBehalfOf(FINANCE, FileCategory.DOCUMENT)).isFalse();
    }
}
