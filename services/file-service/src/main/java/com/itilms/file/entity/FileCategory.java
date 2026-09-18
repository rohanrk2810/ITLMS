package com.itilms.file.entity;

/**
 * What an uploaded file is for. Drives three things at once: who may upload
 * it, who may read it back, and the size limit it is checked against
 * (`itilms.storage.max-size-by-category` in {@code config-repo/file-service.yml}).
 */
public enum FileCategory {
    /** A profile photo. Visible to any signed-in user, like a display name. */
    AVATAR,
    /** An admission or identity document. Owner and staff only. */
    DOCUMENT,
    /** A brief or resource a trainer attaches to an assignment. Course content, openly readable. */
    ASSIGNMENT,
    /** A student's attachment on their own submission. Owner, trainers and staff. */
    SUBMISSION,
    /** Course material attached to a lesson. Course content, openly readable. */
    LESSON_RESOURCE,
    /** A certificate PDF. Owner and staff only. */
    CERTIFICATE,
    /** A fee receipt. Owner and finance desk only. */
    RECEIPT,
    /** A resume attached to a placement application. Owner, staff and the placement desk. */
    RESUME
}
