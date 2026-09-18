package com.itilms.course.service;

import java.util.List;

import com.itilms.course.dto.request.LessonRequest;
import com.itilms.course.dto.request.ModuleRequest;
import com.itilms.course.dto.response.LessonResponse;
import com.itilms.course.dto.response.ModuleResponse;

/**
 * Building a course's contents (Doc S6.8).
 *
 * <p>Separate from {@link CourseService} because the two are used at different
 * times by different people: a coordinator writes the course description once,
 * a trainer adds lessons to it all term.
 */
public interface CurriculumService {

    List<ModuleResponse> listModules(Long courseId);

    ModuleResponse addModule(Long courseId, ModuleRequest request);

    ModuleResponse updateModule(Long moduleId, ModuleRequest request);

    /**
     * Removes a module and everything in it.
     *
     * <p>Refused once students have recorded progress against its lessons —
     * deleting material people have already completed would silently rewrite
     * their history and could revoke a certificate they have already been given.
     */
    void deleteModule(Long moduleId);

    List<LessonResponse> listLessons(Long moduleId);

    LessonResponse addLesson(Long moduleId, LessonRequest request);

    LessonResponse updateLesson(Long lessonId, LessonRequest request);

    void deleteLesson(Long lessonId);

    /** Reorders modules within a course, or lessons within a module. */
    void reorderModules(Long courseId, List<Long> orderedModuleIds);

    void reorderLessons(Long moduleId, List<Long> orderedLessonIds);
}
