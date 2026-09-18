package com.itilms.course.service.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.common.event.CourseProgressUpdatedEvent;
import com.itilms.common.event.DomainEvent;
import com.itilms.common.event.EnrollmentCreatedEvent;
import com.itilms.common.event.EventPublisher;
import com.itilms.common.event.KafkaTopics;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.ForbiddenOperationException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.common.security.AppPrincipal;
import com.itilms.common.security.SecurityUtils;
import com.itilms.course.dto.request.LessonProgressRequest;
import com.itilms.course.dto.response.ProgressResponse;
import com.itilms.course.entity.CourseEnrollment;
import com.itilms.course.entity.EnrollmentStatus;
import com.itilms.course.entity.LessonProgress;
import com.itilms.course.repository.CourseEnrollmentRepository;
import com.itilms.course.repository.CourseRepository;
import com.itilms.course.repository.LessonProgressRepository;
import com.itilms.course.repository.LessonRepository;
import com.itilms.course.service.ProgressService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProgressServiceImpl implements ProgressService {

    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final CourseEnrollmentRepository enrollmentRepository;
    private final LessonProgressRepository progressRepository;
    private final EventPublisher events;

    // -----------------------------------------------------------------
    // Recording
    // -----------------------------------------------------------------

    @Override
    @Transactional
    public ProgressResponse recordProgress(Long lessonId, LessonProgressRequest request) {
        AppPrincipal principal = SecurityUtils.requirePrincipal();
        if (!principal.isStudent() || principal.profileId() == null) {
            throw new ForbiddenOperationException("Only an enrolled student can record lesson progress");
        }

        if (!lessonRepository.existsById(lessonId)) {
            throw new ResourceNotFoundException("Lesson", lessonId);
        }

        Long courseId = courseRepository.findCourseIdByLessonId(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", lessonId));

        // Doc S14: content is reachable only through enrolment. Resolving the
        // enrolment from the caller's own profile - not from anything in the
        // request - is what makes that true rather than merely intended.
        CourseEnrollment enrollment = enrollmentRepository
                .findByStudentIdAndCourseId(principal.profileId(), courseId)
                .orElseThrow(() -> new ForbiddenOperationException(
                        "You are not enrolled in this course"));

        if (!enrollment.getStatus().allowsProgress()) {
            throw new BusinessRuleException(
                    "Your enrolment is %s, so progress can no longer be recorded."
                            .formatted(enrollment.getStatus().name().toLowerCase()));
        }

        LessonProgress progress = progressRepository
                .findByEnrollmentIdAndLessonId(enrollment.getId(), lessonId)
                .orElseGet(() -> LessonProgress.builder()
                        .enrollmentId(enrollment.getId())
                        .lessonId(lessonId)
                        .build());

        if (request.watchedSeconds() != null) {
            progress.recordWatched(request.watchedSeconds());
        }
        if (Boolean.TRUE.equals(request.completed())) {
            progress.markComplete();
        } else if (Boolean.FALSE.equals(request.completed())) {
            progress.markIncomplete();
        }
        progressRepository.save(progress);

        recalculate(enrollment, true);
        return ProgressResponse.from(enrollment);
    }

    // -----------------------------------------------------------------
    // Reading
    // -----------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<ProgressResponse> myProgress(Long studentId) {
        return enrollmentRepository.findByStudentId(studentId).stream()
                .map(ProgressResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProgressResponse progressFor(Long studentId, Long courseId) {
        // The endpoint is open to any signed-in user because other services
        // call it on a student's behalf. The ownership check is therefore here:
        // without it, a student could read a classmate's progress by changing
        // the id in the URL.
        SecurityUtils.requireStudentOwnershipOrStaff(studentId);
        return enrollmentRepository.findByStudentIdAndCourseId(studentId, courseId)
                .map(ProgressResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "This student is not enrolled in that course"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProgressResponse> batchProgress(Long batchId) {
        return enrollmentRepository.findByBatchId(batchId).stream()
                .map(ProgressResponse::from).toList();
    }

    // -----------------------------------------------------------------
    // Maintenance
    // -----------------------------------------------------------------

    @Override
    @Transactional
    public void recalculateCourse(Long courseId) {
        List<CourseEnrollment> enrollments = enrollmentRepository.findActiveByCourseId(courseId);
        if (enrollments.isEmpty()) {
            return;
        }
        // Events are suppressed here. A curriculum edit can touch hundreds of
        // enrolments at once, and emitting a progress event for each would
        // flood notification-service with "your progress changed" messages that
        // the student did nothing to cause.
        enrollments.forEach(enrollment -> recalculate(enrollment, false));
        log.info("Recalculated progress for {} enrolment(s) on course {}", enrollments.size(), courseId);
    }

    @Override
    @Transactional
    public void mirrorEnrollment(EnrollmentCreatedEvent event) {
        if (enrollmentRepository.existsByEnrollmentId(event.enrollmentId())) {
            log.debug("Enrolment {} already mirrored; ignoring redelivery", event.enrollmentId());
            return;
        }
        if (!courseRepository.existsById(event.courseId())) {
            log.warn("EnrollmentCreatedEvent references unknown course {}; skipping", event.courseId());
            return;
        }

        CourseEnrollment enrollment = enrollmentRepository.save(CourseEnrollment.builder()
                .enrollmentId(event.enrollmentId())
                .studentId(event.studentId())
                .userId(event.userId())
                .courseId(event.courseId())
                .batchId(event.batchId())
                .status(EnrollmentStatus.ACTIVE)
                .totalLessons(courseRepository.countMandatoryLessons(event.courseId()))
                .build());

        log.info("Mirrored enrolment {} for student {} on course {} ({} mandatory lesson(s))",
                event.enrollmentId(), event.studentId(), event.courseId(), enrollment.getTotalLessons());
    }

    @Override
    @Transactional
    public void closeEnrollment(Long enrollmentId, String status) {
        enrollmentRepository.findByEnrollmentId(enrollmentId).ifPresent(enrollment -> {
            try {
                enrollment.setStatus(EnrollmentStatus.valueOf(status.toUpperCase()));
            } catch (IllegalArgumentException ex) {
                enrollment.setStatus(EnrollmentStatus.DROPPED);
            }
            enrollmentRepository.save(enrollment);
            log.info("Enrolment {} closed as {}", enrollmentId, enrollment.getStatus());
        });
    }

    // -----------------------------------------------------------------
    // Core recalculation
    // -----------------------------------------------------------------

    /**
     * Recomputes one enrolment's counters and, when asked, announces the result.
     *
     * <p>The denominator is the course's mandatory lessons, counted live. The
     * numerator counts only completions among <em>those</em> lessons, so
     * finishing optional extras cannot push a student to 100%.
     */
    private void recalculate(CourseEnrollment enrollment, boolean publishEvent) {
        List<Long> mandatoryLessonIds = lessonRepository.findMandatoryLessonIds(enrollment.getCourseId());
        int total = mandatoryLessonIds.size();
        int completed = mandatoryLessonIds.isEmpty()
                ? 0
                : progressRepository.countCompletedAmong(enrollment.getId(), mandatoryLessonIds);

        boolean wasComplete = enrollment.allLessonsComplete();
        enrollment.recalculate(completed, total);
        enrollmentRepository.save(enrollment);

        if (!publishEvent) {
            return;
        }

        events.publishAfterCommit(KafkaTopics.COURSE_PROGRESS_UPDATED, new CourseProgressUpdatedEvent(
                DomainEvent.newId(), Instant.now(),
                enrollment.getEnrollmentId(), enrollment.getStudentId(), enrollment.getCourseId(),
                enrollment.getBatchId(), enrollment.getProgressPercent(),
                enrollment.getCompletedLessons(), enrollment.getTotalLessons(),
                enrollment.allLessonsComplete()));

        // certificate-service is listening for the moment this flips true; it
        // is one of the four completion conditions in Doc S7.3.
        if (!wasComplete && enrollment.allLessonsComplete()) {
            log.info("Student {} completed all mandatory lessons on course {}",
                    enrollment.getStudentId(), enrollment.getCourseId());
            events.notifyUsers(List.of(enrollment.getUserId()), "COURSE_PROGRESS",
                    "You have finished all the lessons",
                    "Every required lesson on your course is complete. "
                            + "Your certificate follows once tests, assignments and attendance are cleared.",
                    "/student/courses/" + enrollment.getCourseId());
        }
    }
}
