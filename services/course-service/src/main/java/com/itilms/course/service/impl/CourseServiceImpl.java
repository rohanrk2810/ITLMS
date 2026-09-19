package com.itilms.course.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.common.dto.PageResponse;
import com.itilms.common.event.DomainEvent;
import com.itilms.common.event.EventPublisher;
import com.itilms.common.event.KafkaTopics;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.DuplicateResourceException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.common.security.AppPrincipal;
import com.itilms.common.security.SecurityUtils;
import com.itilms.course.dto.request.CreateCourseRequest;
import com.itilms.course.dto.request.UpdateCourseRequest;
import com.itilms.course.dto.response.CourseDetailResponse;
import com.itilms.course.dto.response.CourseResponse;
import com.itilms.course.dto.response.CourseSummaryResponse;
import com.itilms.course.dto.response.LessonResponse;
import com.itilms.course.dto.response.ModuleResponse;
import com.itilms.course.dto.response.ProgressResponse;
import com.itilms.course.entity.Course;
import com.itilms.course.entity.CourseLevel;
import com.itilms.course.entity.CourseModule;
import com.itilms.course.entity.CourseStatus;
import com.itilms.course.entity.Lesson;
import com.itilms.course.entity.LessonProgress;
import com.itilms.course.repository.CourseEnrollmentRepository;
import com.itilms.course.repository.CourseModuleRepository;
import com.itilms.course.repository.CourseRepository;
import com.itilms.course.repository.LessonProgressRepository;
import com.itilms.course.repository.LessonRepository;
import com.itilms.course.service.CourseService;
import com.itilms.course.specification.CourseSpecifications;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseServiceImpl implements CourseService {

    private static final String SERVICE_NAME = "course-service";

    private final CourseRepository courseRepository;
    private final CourseModuleRepository moduleRepository;
    private final LessonRepository lessonRepository;
    private final CourseEnrollmentRepository enrollmentRepository;
    private final LessonProgressRepository progressRepository;
    private final EventPublisher events;

    // -----------------------------------------------------------------
    // Read
    // -----------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CourseSummaryResponse> search(String status, String level,
                                                      String query, Pageable pageable) {
        Specification<Course> spec = Specification.allOf(
                CourseSpecifications.hasStatus(status),
                CourseSpecifications.hasLevel(level),
                CourseSpecifications.matches(query));
        return PageResponse.from(courseRepository.findAll(spec, pageable), CourseSummaryResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CourseSummaryResponse> publicCatalog(String level, String query, Pageable pageable) {
        // The status filter is applied here rather than taken from the caller:
        // an anonymous visitor must not be able to ask for DRAFT courses by
        // passing ?status=DRAFT.
        Specification<Course> spec = Specification.allOf(
                CourseSpecifications.published(),
                CourseSpecifications.hasLevel(level),
                CourseSpecifications.matches(query));
        return PageResponse.from(courseRepository.findAll(spec, pageable), CourseSummaryResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public CourseResponse get(Long courseId) {
        Course course = require(courseId);
        return CourseResponse.from(course,
                moduleRepository.countByCourseId(courseId),
                courseRepository.findLessonIdsByCourse(courseId).size());
    }

    /**
     * Assembles the curriculum and decides, lesson by lesson, what this caller
     * may see.
     *
     * <p>The access decision is made once, here on the server, and is visible in
     * the response through {@code accessible}. The client renders a padlock from
     * that flag rather than deciding for itself — so a modified client changes
     * what the padlock looks like, not what data it receives.
     */
    @Override
    @Transactional(readOnly = true)
    public CourseDetailResponse getDetail(Long courseId) {
        Course course = require(courseId);
        AppPrincipal principal = SecurityUtils.currentPrincipal().orElse(null);

        boolean staffView = principal != null && !principal.isStudent();

        // Only an enrolment that still counts opens the content: a student who has been
        // dropped or suspended sees the same locked view as anyone who never enrolled.
        var enrollment = (principal != null && principal.isStudent() && principal.profileId() != null)
                ? enrollmentRepository.findByStudentIdAndCourseId(principal.profileId(), courseId)
                        .filter(e -> e.getStatus().allowsContentAccess())
                : Optional.<com.itilms.course.entity.CourseEnrollment>empty();
        boolean enrolled = enrollment.isPresent();

        // An unpublished course simply does not exist as far as students and
        // anonymous visitors are concerned. The exception is an archived course
        // for someone who already took it: archiving removes it from the catalog,
        // not from the people who hold it (see archive()).
        boolean visible = course.getStatus().isVisibleToStudents()
                || (enrolled && course.getStatus() == CourseStatus.ARCHIVED);
        if (!staffView && !visible) {
            throw new ResourceNotFoundException("Course", courseId);
        }

        boolean unlockEverything = staffView || enrolled;

        // One query for all progress rows, keyed by lesson, so building the
        // response does not issue a lookup per lesson.
        Map<Long, LessonProgress> progressByLesson = enrollment
                .map(e -> progressRepository.findByEnrollmentId(e.getId()).stream()
                        .collect(java.util.stream.Collectors.toMap(
                                LessonProgress::getLessonId, p -> p, (a, b) -> a)))
                .orElse(Map.of());

        List<CourseModule> modules = moduleRepository.findByCourseIdOrderBySequenceNo(courseId);
        List<Long> moduleIds = modules.stream().map(CourseModule::getId).toList();

        Map<Long, List<Lesson>> lessonsByModule = moduleIds.isEmpty()
                ? Map.of()
                : lessonRepository.findByModuleIdInOrderByModuleIdAscSequenceNoAsc(moduleIds).stream()
                        .collect(java.util.stream.Collectors.groupingBy(Lesson::getModuleId));

        List<ModuleResponse> moduleResponses = new ArrayList<>(modules.size());
        long lessonCount = 0;

        for (CourseModule module : modules) {
            List<Lesson> lessons = lessonsByModule.getOrDefault(module.getId(), List.of());
            lessonCount += lessons.size();

            List<LessonResponse> lessonResponses = lessons.stream()
                    .map(lesson -> {
                        if (unlockEverything || lesson.isPreview()) {
                            LessonProgress progress = progressByLesson.get(lesson.getId());
                            return LessonResponse.unlocked(lesson,
                                    enrolled ? (progress != null && progress.isCompleted()) : null,
                                    enrolled ? (progress == null ? 0 : progress.getWatchedSeconds()) : null);
                        }
                        return LessonResponse.locked(lesson);
                    })
                    .toList();

            moduleResponses.add(ModuleResponse.from(module, lessonResponses));
        }

        return new CourseDetailResponse(
                CourseResponse.from(course, modules.size(), lessonCount),
                moduleResponses,
                enrolled,
                enrollment.map(ProgressResponse::from).orElse(null));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseSummaryResponse> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return courseRepository.findByIdIn(ids).stream().map(CourseSummaryResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> counts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (CourseStatus status : CourseStatus.values()) {
            counts.put(status.name(), courseRepository.countByStatus(status));
        }
        counts.put("TOTAL", courseRepository.count());
        return counts;
    }

    // -----------------------------------------------------------------
    // Write
    // -----------------------------------------------------------------

    @Override
    @Transactional
    public CourseResponse create(CreateCourseRequest request) {
        String code = request.code().trim().toUpperCase();
        if (courseRepository.existsByCodeIgnoreCase(code)) {
            throw DuplicateResourceException.of("course", "code", code);
        }

        Course course = courseRepository.save(Course.builder()
                .title(request.title().trim())
                .code(code)
                .summary(trim(request.summary()))
                .description(trim(request.description()))
                .learningOutcomes(trim(request.learningOutcomes()))
                .prerequisites(trim(request.prerequisites()))
                .technologyStack(trim(request.technologyStack()))
                .durationHours(request.durationHours() == null ? 0 : request.durationHours())
                .level(parseLevel(request.level()))
                .fee(request.fee() == null ? BigDecimal.ZERO : request.fee())
                .thumbnailRef(trim(request.thumbnailRef()))
                .status(CourseStatus.DRAFT)
                .build());

        events.audit(SERVICE_NAME, "COURSE_CREATED", "Course", course.getId(), null,
                Map.of("code", code, "title", course.getTitle()));

        log.info("Created course {} ({})", course.getId(), code);
        return CourseResponse.from(course);
    }

    @Override
    @Transactional
    public CourseResponse update(Long courseId, UpdateCourseRequest request) {
        Course course = require(courseId);
        if (!course.getStatus().isEditable()) {
            throw new BusinessRuleException(
                    "This course is archived. Restore it before making changes.");
        }

        String code = request.code().trim().toUpperCase();
        if (courseRepository.existsByCodeExcluding(code, courseId)) {
            throw DuplicateResourceException.of("course", "code", code);
        }

        course.setTitle(request.title().trim());
        course.setCode(code);
        course.setSummary(trim(request.summary()));
        course.setDescription(trim(request.description()));
        course.setLearningOutcomes(trim(request.learningOutcomes()));
        course.setPrerequisites(trim(request.prerequisites()));
        course.setTechnologyStack(trim(request.technologyStack()));
        course.setDurationHours(request.durationHours() == null ? 0 : request.durationHours());
        course.setLevel(parseLevel(request.level()));
        course.setFee(request.fee() == null ? BigDecimal.ZERO : request.fee());
        course.setThumbnailRef(trim(request.thumbnailRef()));

        courseRepository.save(course);
        events.audit(SERVICE_NAME, "COURSE_UPDATED", "Course", courseId, null, null);

        return CourseResponse.from(course);
    }

    /**
     * Publishing is where the completeness rules bite.
     *
     * <p>Doc S14: a course cannot be published without title, description and at
     * least one lesson. Each check names the missing thing, because "course is
     * incomplete" leaves an author hunting through six tabs for the field.
     */
    @Override
    @Transactional
    public CourseResponse publish(Long courseId) {
        Course course = require(courseId);

        if (course.getStatus() == CourseStatus.PUBLISHED) {
            return CourseResponse.from(course);
        }

        List<String> missing = new ArrayList<>();
        if (isBlank(course.getSummary())) {
            missing.add("a short summary for the catalog card");
        }
        if (isBlank(course.getDescription())) {
            missing.add("a full description");
        }
        if (moduleRepository.countByCourseId(courseId) == 0) {
            missing.add("at least one module");
        }

        List<Long> lessonIds = courseRepository.findLessonIdsByCourse(courseId);
        if (lessonIds.isEmpty()) {
            missing.add("at least one lesson");
        }
        if (courseRepository.countMandatoryLessons(courseId) == 0 && !lessonIds.isEmpty()) {
            // Every lesson is optional, so nobody could ever complete the course
            // and no certificate could ever be issued.
            missing.add("at least one mandatory lesson (all lessons are currently optional)");
        }

        if (!missing.isEmpty()) {
            throw new BusinessRuleException(
                    "This course cannot be published yet. It still needs " + String.join(", ", missing) + ".");
        }

        course.publish();
        courseRepository.save(course);

        events.publishAfterCommit(KafkaTopics.COURSE_PUBLISHED,
                new com.itilms.common.event.CoursePublishedEvent(
                        DomainEvent.newId(), java.time.Instant.now(),
                        courseId, course.getCode(), course.getTitle(), course.getLevel().name(),
                        course.getFee(), (int) moduleRepository.countByCourseId(courseId),
                        lessonIds.size(), SecurityUtils.currentUserId()));

        events.audit(SERVICE_NAME, "COURSE_PUBLISHED", "Course", courseId,
                Map.of("status", CourseStatus.DRAFT.name()),
                Map.of("status", CourseStatus.PUBLISHED.name()));

        log.info("Published course {} ({})", courseId, course.getCode());
        return CourseResponse.from(course);
    }

    @Override
    @Transactional
    public CourseResponse archive(Long courseId) {
        Course course = require(courseId);
        CourseStatus previous = course.getStatus();

        // Archiving is not deletion. Students who took the course keep their
        // content, results and certificates; it simply leaves the catalog.
        course.archive();
        courseRepository.save(course);

        events.audit(SERVICE_NAME, "COURSE_ARCHIVED", "Course", courseId,
                Map.of("status", previous.name()),
                Map.of("status", CourseStatus.ARCHIVED.name()));

        log.info("Archived course {} ({})", courseId, course.getCode());
        return CourseResponse.from(course);
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private Course require(Long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", courseId));
    }

    private CourseLevel parseLevel(String value) {
        if (value == null || value.isBlank()) {
            return CourseLevel.BEGINNER;
        }
        try {
            return CourseLevel.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("Level must be BEGINNER, INTERMEDIATE or ADVANCED");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
