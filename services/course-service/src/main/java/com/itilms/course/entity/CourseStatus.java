package com.itilms.course.entity;

/**
 * Publication state (Doc S6.5).
 *
 * <p>DRAFT is freely editable and invisible to students. PUBLISHED is visible
 * and requires complete metadata. ARCHIVED hides a course from the catalog
 * without deleting it, because students who took it still need their content,
 * their results and their certificate.
 */
public enum CourseStatus {
    DRAFT,
    PUBLISHED,
    ARCHIVED;

    public boolean isVisibleToStudents() {
        return this == PUBLISHED;
    }

    public boolean isEditable() {
        return this != ARCHIVED;
    }
}
