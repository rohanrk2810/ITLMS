package com.itilms.course.service;

import java.util.List;

import com.itilms.common.event.EnrollmentCreatedEvent;
import com.itilms.course.dto.request.LessonProgressRequest;
import com.itilms.course.dto.response.ProgressResponse;
import com.itilms.course.dto.response.StudentCourseProgressResponse;

/** Tracking how far students have got (Doc S7.2, S7.3). */
public interface ProgressService {

    /**
     * Every course a student is taking, module by module, for their progress report. Internal: no ownership
     * check here, because only reporting-service reaches it (the gateway hides /internal/), and it decides
     * who may see which student's report.
     */
    List<StudentCourseProgressResponse> courseProgressOf(Long studentId);

    /**
     * Records watch time or a completion tick against a lesson.
     *
     * <p>Only the enrolled student may call this for themselves; the enrolment
     * is resolved from the caller's own profile id, never from the request, so
     * there is no parameter through which one student could mark another's
     * lesson complete.
     */
    ProgressResponse recordProgress(Long lessonId, LessonProgressRequest request);

    /** Everything the signed-in student is enrolled in, with progress. */
    List<ProgressResponse> myProgress(Long studentId);

    ProgressResponse progressFor(Long studentId, Long courseId);

    /** Progress for a whole batch — the trainer's performance view. */
    List<ProgressResponse> batchProgress(Long batchId);

    /**
     * Recomputes every active enrolment on a course.
     *
     * <p>Called when the curriculum changes. Adding a mandatory lesson has to
     * move percentages down, and removing one has to move them up; leaving the
     * stored figures alone would let a certificate be issued against a
     * curriculum that no longer exists.
     */
    void recalculateCourse(Long courseId);

    /** Creates the local enrolment mirror when batch-service enrols someone. */
    void mirrorEnrollment(EnrollmentCreatedEvent event);

    void closeEnrollment(Long enrollmentId, String status);
}
