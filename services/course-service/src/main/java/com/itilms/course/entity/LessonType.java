package com.itilms.course.entity;

/** What kind of material a lesson holds (Doc S6.8). */
public enum LessonType {
    /** Hosted or external video; content_url carries the player source. */
    VIDEO,
    /** Uploaded PDF, referenced by file-service handle. */
    PDF,
    /** Trainer notes stored as text. */
    NOTE,
    /** An external reference - documentation, an article, a repository. */
    LINK,
    /** Rich text written directly into the lesson. */
    TEXT;

    /** True when the material lives in file-service rather than at a URL. */
    public boolean isFileBacked() {
        return this == PDF;
    }
}
