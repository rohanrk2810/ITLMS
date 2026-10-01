package com.itilms.course.service.impl;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.common.code.CodeLanguage;
import com.itilms.common.event.DomainEvent;
import com.itilms.common.event.EventPublisher;
import com.itilms.common.event.KafkaTopics;
import com.itilms.common.event.LessonPublishedEvent;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.course.dto.request.LessonRequest;
import com.itilms.course.dto.request.ModuleRequest;
import com.itilms.course.dto.response.LessonResponse;
import com.itilms.course.dto.response.ModuleResponse;
import com.itilms.course.entity.Course;
import com.itilms.course.entity.CourseModule;
import com.itilms.course.entity.CourseStatus;
import com.itilms.course.entity.Lesson;
import com.itilms.course.entity.LessonType;
import com.itilms.course.repository.CourseModuleRepository;
import com.itilms.course.repository.CourseRepository;
import com.itilms.course.repository.LessonProgressRepository;
import com.itilms.course.repository.LessonRepository;
import com.itilms.course.service.CurriculumService;
import com.itilms.course.service.ProgressService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CurriculumServiceImpl implements CurriculumService {

    private static final String SERVICE_NAME = "course-service";

    private final CourseRepository courseRepository;
    private final CourseModuleRepository moduleRepository;
    private final LessonRepository lessonRepository;
    private final LessonProgressRepository progressRepository;
    private final ProgressService progressService;
    private final EventPublisher events;

    // -----------------------------------------------------------------
    // Modules
    // -----------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<ModuleResponse> listModules(Long courseId) {
        requireCourse(courseId);
        return moduleRepository.findByCourseIdOrderBySequenceNo(courseId).stream()
                .map(module -> ModuleResponse.from(module,
                        lessonRepository.findByModuleIdOrderBySequenceNo(module.getId()).stream()
                                .map(lesson -> LessonResponse.unlocked(lesson, null, null))
                                .toList()))
                .toList();
    }

    @Override
    @Transactional
    public ModuleResponse addModule(Long courseId, ModuleRequest request) {
        Course course = requireEditableCourse(courseId);

        int sequence = request.sequenceNo() != null
                ? request.sequenceNo()
                : moduleRepository.nextSequenceNo(courseId);

        if (moduleRepository.findByCourseIdAndSequenceNo(courseId, sequence).isPresent()) {
            throw new BusinessRuleException(
                    "Position %d is already taken in this course. Leave the position blank to append."
                            .formatted(sequence));
        }

        CourseModule module = moduleRepository.save(CourseModule.builder()
                .courseId(courseId)
                .title(request.title().trim())
                .description(trim(request.description()))
                .sequenceNo(sequence)
                .build());

        events.audit(SERVICE_NAME, "MODULE_ADDED", "CourseModule", module.getId(), null,
                Map.of("courseId", courseId, "title", module.getTitle()));

        log.debug("Added module {} to course {} at position {}", module.getId(), courseId, sequence);
        return ModuleResponse.from(module, List.of());
    }

    @Override
    @Transactional
    public ModuleResponse updateModule(Long moduleId, ModuleRequest request) {
        CourseModule module = requireModule(moduleId);
        requireEditableCourse(module.getCourseId());

        module.setTitle(request.title().trim());
        module.setDescription(trim(request.description()));
        moduleRepository.save(module);

        return ModuleResponse.from(module,
                lessonRepository.findByModuleIdOrderBySequenceNo(moduleId).stream()
                        .map(lesson -> LessonResponse.unlocked(lesson, null, null))
                        .toList());
    }

    @Override
    @Transactional
    public void deleteModule(Long moduleId) {
        CourseModule module = requireModule(moduleId);
        Long courseId = module.getCourseId();
        requireEditableCourse(courseId);

        List<Lesson> lessons = lessonRepository.findByModuleIdOrderBySequenceNo(moduleId);
        for (Lesson lesson : lessons) {
            guardAgainstDeletingStudiedMaterial(lesson.getId(), lesson.getTitle());
        }

        moduleRepository.delete(module);   // cascades to its lessons
        events.audit(SERVICE_NAME, "MODULE_DELETED", "CourseModule", moduleId,
                Map.of("title", module.getTitle(), "lessonCount", lessons.size()), null);

        // The denominator just changed for everyone on this course.
        progressService.recalculateCourse(courseId);
        log.info("Deleted module {} ({} lesson(s)) from course {}", moduleId, lessons.size(), courseId);
    }

    // -----------------------------------------------------------------
    // Lessons
    // -----------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<LessonResponse> listLessons(Long moduleId) {
        requireModule(moduleId);
        return lessonRepository.findByModuleIdOrderBySequenceNo(moduleId).stream()
                .map(lesson -> LessonResponse.unlocked(lesson, null, null))
                .toList();
    }

    @Override
    @Transactional
    public LessonResponse addLesson(Long moduleId, LessonRequest request) {
        CourseModule module = requireModule(moduleId);
        Course course = requireEditableCourse(module.getCourseId());

        LessonType type = parseType(request.type());
        validateContent(type, request);
        CodeLanguage codeLanguage = parseCodeLanguage(request);

        int sequence = request.sequenceNo() != null
                ? request.sequenceNo()
                : lessonRepository.nextSequenceNo(moduleId);

        Lesson lesson = lessonRepository.save(Lesson.builder()
                .moduleId(moduleId)
                .title(request.title().trim())
                .type(type)
                .contentUrl(trim(request.contentUrl()))
                .contentFileRef(trim(request.contentFileRef()))
                .textContent(trim(request.textContent()))
                .codeLanguage(codeLanguage)
                .starterCode(starterCodeOrNull(codeLanguage, request))
                .allowLanguageChoice(choiceAllowed(codeLanguage, request))
                .durationMinutes(request.durationMinutes() == null ? 0 : request.durationMinutes())
                .sequenceNo(sequence)
                .preview(request.previewOrDefault())
                .mandatory(request.mandatoryOrDefault())
                .build());

        // Adding a mandatory lesson lowers everyone's percentage, and that has
        // to be reflected immediately - otherwise a student sitting at 100%
        // stays there while new required material goes unnoticed.
        if (lesson.isMandatory()) {
            progressService.recalculateCourse(course.getId());
        }

        // Doc S16: batch students are told when new material appears. Only for
        // a live course - announcing lessons on an unpublished draft would
        // notify people about something they cannot open.
        if (course.getStatus() == CourseStatus.PUBLISHED) {
            events.publishAfterCommit(KafkaTopics.LESSON_PUBLISHED, new LessonPublishedEvent(
                    DomainEvent.newId(), Instant.now(),
                    lesson.getId(), moduleId, course.getId(), course.getTitle(),
                    lesson.getTitle(), type.name()));
        }

        events.audit(SERVICE_NAME, "LESSON_ADDED", "Lesson", lesson.getId(), null,
                Map.of("moduleId", moduleId, "title", lesson.getTitle(), "type", type.name()));

        return LessonResponse.unlocked(lesson, null, null);
    }

    @Override
    @Transactional
    public LessonResponse updateLesson(Long lessonId, LessonRequest request) {
        Lesson lesson = requireLesson(lessonId);
        CourseModule module = requireModule(lesson.getModuleId());
        requireEditableCourse(module.getCourseId());

        LessonType type = parseType(request.type());
        validateContent(type, request);
        CodeLanguage codeLanguage = parseCodeLanguage(request);

        boolean wasMandatory = lesson.isMandatory();

        lesson.setTitle(request.title().trim());
        lesson.setType(type);
        lesson.setContentUrl(trim(request.contentUrl()));
        lesson.setContentFileRef(trim(request.contentFileRef()));
        lesson.setTextContent(trim(request.textContent()));
        lesson.setCodeLanguage(codeLanguage);
        lesson.setStarterCode(starterCodeOrNull(codeLanguage, request));
        lesson.setAllowLanguageChoice(choiceAllowed(codeLanguage, request));
        lesson.setDurationMinutes(request.durationMinutes() == null ? 0 : request.durationMinutes());
        lesson.setPreview(request.previewOrDefault());
        lesson.setMandatory(request.mandatoryOrDefault());
        lessonRepository.save(lesson);

        if (wasMandatory != lesson.isMandatory()) {
            progressService.recalculateCourse(module.getCourseId());
        }

        return LessonResponse.unlocked(lesson, null, null);
    }

    @Override
    @Transactional
    public void deleteLesson(Long lessonId) {
        Lesson lesson = requireLesson(lessonId);
        CourseModule module = requireModule(lesson.getModuleId());
        requireEditableCourse(module.getCourseId());

        guardAgainstDeletingStudiedMaterial(lessonId, lesson.getTitle());

        lessonRepository.delete(lesson);
        events.audit(SERVICE_NAME, "LESSON_DELETED", "Lesson", lessonId,
                Map.of("title", lesson.getTitle()), null);

        progressService.recalculateCourse(module.getCourseId());
        log.info("Deleted lesson {} from module {}", lessonId, module.getId());
    }

    // -----------------------------------------------------------------
    // Ordering
    // -----------------------------------------------------------------

    /**
     * Rewrites positions from a drag-and-drop reorder.
     *
     * <p>Done in two passes. The unique constraint on (course, position) means a
     * direct rewrite trips over itself the moment two items swap, so everything
     * is first parked in a negative range that no real row uses, then written
     * down into its final position.
     */
    @Override
    @Transactional
    public void reorderModules(Long courseId, List<Long> orderedModuleIds) {
        requireEditableCourse(courseId);
        List<CourseModule> modules = moduleRepository.findByCourseIdOrderBySequenceNo(courseId);

        validateReorder(modules.stream().map(CourseModule::getId).toList(), orderedModuleIds, "module");

        Map<Long, CourseModule> byId = modules.stream()
                .collect(java.util.stream.Collectors.toMap(CourseModule::getId, m -> m));

        for (CourseModule module : modules) {
            module.setSequenceNo(-module.getSequenceNo());
        }
        moduleRepository.saveAllAndFlush(modules);

        for (int index = 0; index < orderedModuleIds.size(); index++) {
            byId.get(orderedModuleIds.get(index)).setSequenceNo(index + 1);
        }
        moduleRepository.saveAllAndFlush(modules);

        log.debug("Reordered {} modules in course {}", modules.size(), courseId);
    }

    @Override
    @Transactional
    public void reorderLessons(Long moduleId, List<Long> orderedLessonIds) {
        CourseModule module = requireModule(moduleId);
        requireEditableCourse(module.getCourseId());

        List<Lesson> lessons = lessonRepository.findByModuleIdOrderBySequenceNo(moduleId);
        validateReorder(lessons.stream().map(Lesson::getId).toList(), orderedLessonIds, "lesson");

        Map<Long, Lesson> byId = lessons.stream()
                .collect(java.util.stream.Collectors.toMap(Lesson::getId, l -> l));

        for (Lesson lesson : lessons) {
            lesson.setSequenceNo(-lesson.getSequenceNo());
        }
        lessonRepository.saveAllAndFlush(lessons);

        for (int index = 0; index < orderedLessonIds.size(); index++) {
            byId.get(orderedLessonIds.get(index)).setSequenceNo(index + 1);
        }
        lessonRepository.saveAllAndFlush(lessons);
    }

    // -----------------------------------------------------------------
    // Guards
    // -----------------------------------------------------------------

    /**
     * Blocks deletion of material students have already completed.
     *
     * <p>Their progress rows would go with it, their percentage would change,
     * and a certificate already issued on the old curriculum would no longer be
     * defensible. Marking the lesson optional achieves what the trainer usually
     * wants without rewriting anyone's record.
     */
    private void guardAgainstDeletingStudiedMaterial(Long lessonId, String title) {
        long completions = progressRepository.countByLessonIdAndCompletedTrue(lessonId);

        if (completions > 0) {
            throw new BusinessRuleException(
                    ("\"%s\" has been completed by %d student(s) and cannot be deleted. "
                            + "Mark it optional instead, which removes it from the completion "
                            + "requirement without erasing their records.")
                            .formatted(title, completions));
        }
    }

    private void validateContent(LessonType type, LessonRequest request) {
        boolean hasUrl = request.contentUrl() != null && !request.contentUrl().isBlank();
        boolean hasFile = request.contentFileRef() != null && !request.contentFileRef().isBlank();
        boolean hasText = request.textContent() != null && !request.textContent().isBlank();

        if (!hasUrl && !hasFile && !hasText) {
            throw new BusinessRuleException(
                    "A lesson needs some material: a link, an uploaded file, or written content.");
        }

        switch (type) {
            case VIDEO, LINK -> {
                if (!hasUrl) {
                    throw new BusinessRuleException(
                            "A %s lesson needs a URL in contentUrl".formatted(type.name().toLowerCase()));
                }
            }
            case PDF -> {
                if (!hasFile && !hasUrl) {
                    throw new BusinessRuleException(
                            "A PDF lesson needs an uploaded file reference or a URL");
                }
            }
            case NOTE, TEXT -> {
                if (!hasText) {
                    throw new BusinessRuleException(
                            "A %s lesson needs written content".formatted(type.name().toLowerCase()));
                }
            }
        }
    }

    /** Only a practice lesson can offer a choice, and a SQL lesson cannot (it is not interchangeable with the others). */
    private static boolean choiceAllowed(CodeLanguage language, LessonRequest request) {
        return language != null && language != CodeLanguage.SQL && Boolean.TRUE.equals(request.allowLanguageChoice());
    }

    /** The practice-editor language, or null when the lesson has none. */
    private CodeLanguage parseCodeLanguage(LessonRequest request) {
        boolean hasLanguage = request.codeLanguage() != null && !request.codeLanguage().isBlank();
        if (!hasLanguage) {
            if (request.starterCode() != null && !request.starterCode().isBlank()) {
                throw new BusinessRuleException(
                        "Starter code needs a practice language: choose " + CodeLanguage.allowedList());
            }
            return null;
        }
        return CodeLanguage.parse(request.codeLanguage()).orElseThrow(() ->
                new BusinessRuleException("Practice language must be " + CodeLanguage.allowedList()));
    }

    /** Kept verbatim - indentation is part of code - but never stored without a language. */
    private String starterCodeOrNull(CodeLanguage language, LessonRequest request) {
        if (language == null || request.starterCode() == null || request.starterCode().isBlank()) {
            return null;
        }
        return request.starterCode();
    }

    private void validateReorder(List<Long> existingIds, List<Long> submittedIds, String noun) {
        if (submittedIds == null || submittedIds.size() != existingIds.size()
                || !submittedIds.containsAll(existingIds)) {
            throw new BusinessRuleException(
                    "The reorder must list every %s exactly once. Expected %d id(s)."
                            .formatted(noun, existingIds.size()));
        }
    }

    private Course requireCourse(Long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", courseId));
    }

    private Course requireEditableCourse(Long courseId) {
        Course course = requireCourse(courseId);
        if (!course.getStatus().isEditable()) {
            throw new BusinessRuleException("This course is archived and its content cannot be changed");
        }
        return course;
    }

    private CourseModule requireModule(Long moduleId) {
        return moduleRepository.findById(moduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Module", moduleId));
    }

    private Lesson requireLesson(Long lessonId) {
        return lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", lessonId));
    }

    private LessonType parseType(String value) {
        try {
            return LessonType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new BusinessRuleException("Lesson type must be VIDEO, PDF, NOTE, LINK or TEXT");
        }
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
