package com.itilms.file.service;

import java.util.EnumSet;
import java.util.Set;

import com.itilms.common.security.AppPrincipal;
import com.itilms.common.security.Roles;
import com.itilms.file.entity.FileCategory;

/**
 * Who may upload, read and delete a file, by {@link FileCategory}.
 *
 * <p>Course content (assignment briefs, lesson resources) and avatars are
 * openly readable once signed in - access to the lesson or assignment itself
 * is gated elsewhere, the same simplification already recorded for L1/L2 in
 * docs/02-documentation-review.md. Anything naming a person - a document, a
 * submission, a certificate, a receipt - is owner-and-staff only.
 */
public final class FileAccessRules {

    private FileAccessRules() {
    }

    private static final Set<FileCategory> OPENLY_READABLE =
            EnumSet.of(FileCategory.AVATAR, FileCategory.ASSIGNMENT, FileCategory.LESSON_RESOURCE);

    public static boolean canUpload(AppPrincipal caller, FileCategory category) {
        return switch (category) {
            case AVATAR -> true;
            case DOCUMENT -> caller.isStudent() || caller.isStaff();
            case ASSIGNMENT, LESSON_RESOURCE -> caller.isStaff() || caller.isTrainer();
            case SUBMISSION -> caller.isStudent();
            case CERTIFICATE -> caller.isStaff();
            case RECEIPT -> caller.isStaff() || Roles.FINANCE.equals(caller.role());
        };
    }

    public static boolean canRead(AppPrincipal caller, FileCategory category, Long ownerUserId) {
        if (OPENLY_READABLE.contains(category)) {
            return true;
        }
        if (caller.userId() != null && caller.userId().equals(ownerUserId)) {
            return true;
        }
        return switch (category) {
            case DOCUMENT, CERTIFICATE -> caller.isStaff();
            case SUBMISSION -> caller.isStaff() || caller.isTrainer();
            case RECEIPT -> caller.isStaff() || Roles.FINANCE.equals(caller.role());
            default -> false;
        };
    }

    /** The uploader may remove their own mistake; otherwise this is staff work. */
    public static boolean canDelete(AppPrincipal caller, Long uploadedBy) {
        return caller.isStaff() || (caller.userId() != null && caller.userId().equals(uploadedBy));
    }

    /** Staff (and finance, for receipts) may act on someone else's behalf; everyone else uploads only for themselves. */
    public static boolean canActOnBehalfOf(AppPrincipal caller, FileCategory category) {
        return caller.isStaff() || (category == FileCategory.RECEIPT && Roles.FINANCE.equals(caller.role()));
    }
}
