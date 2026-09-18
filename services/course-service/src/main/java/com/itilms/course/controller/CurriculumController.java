package com.itilms.course.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.itilms.common.dto.ApiMessage;
import com.itilms.common.security.Roles;
import com.itilms.course.dto.request.LessonRequest;
import com.itilms.course.dto.request.ModuleRequest;
import com.itilms.course.dto.response.LessonResponse;
import com.itilms.course.dto.response.ModuleResponse;
import com.itilms.course.service.CurriculumService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Building a course's contents (Doc S6.8).
 *
 * <p>Trainers may add and edit material — that is the whole point of the trainer
 * role — while structural changes stay with staff.
 */
@Tag(name = "Curriculum", description = "Modules and lessons")
@RestController
@RequiredArgsConstructor
public class CurriculumController {

    private final CurriculumService curriculumService;

    // ---------------------------- Modules ----------------------------

    @Operation(summary = "List a course's modules")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/api/courses/{courseId}/modules")
    public List<ModuleResponse> listModules(@PathVariable Long courseId) {
        return curriculumService.listModules(courseId);
    }

    @Operation(summary = "Add a module", description = "Appended to the end when no position is given.")
    @PreAuthorize(Roles.ACADEMIC)
    @PostMapping("/api/courses/{courseId}/modules")
    public ResponseEntity<ModuleResponse> addModule(@PathVariable Long courseId,
                                                    @Valid @RequestBody ModuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(curriculumService.addModule(courseId, request));
    }

    @Operation(summary = "Update a module")
    @PreAuthorize(Roles.ACADEMIC)
    @PutMapping("/api/modules/{moduleId}")
    public ModuleResponse updateModule(@PathVariable Long moduleId,
                                       @Valid @RequestBody ModuleRequest request) {
        return curriculumService.updateModule(moduleId, request);
    }

    @Operation(summary = "Delete a module",
            description = "Refused when students have already completed lessons inside it.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Deleted"),
            @ApiResponse(responseCode = "422", description = "Students have completed this material")
    })
    @PreAuthorize(Roles.STAFF)
    @DeleteMapping("/api/modules/{moduleId}")
    public ApiMessage deleteModule(@PathVariable Long moduleId) {
        curriculumService.deleteModule(moduleId);
        return ApiMessage.ok("Module deleted");
    }

    @Operation(summary = "Reorder a course's modules",
            description = "Send every module id in the new order.")
    @PreAuthorize(Roles.ACADEMIC)
    @PutMapping("/api/courses/{courseId}/modules/order")
    public ApiMessage reorderModules(@PathVariable Long courseId, @RequestBody List<Long> orderedIds) {
        curriculumService.reorderModules(courseId, orderedIds);
        return ApiMessage.ok("Modules reordered");
    }

    // ---------------------------- Lessons ----------------------------

    @Operation(summary = "List a module's lessons")
    @PreAuthorize(Roles.ACADEMIC)
    @GetMapping("/api/modules/{moduleId}/lessons")
    public List<LessonResponse> listLessons(@PathVariable Long moduleId) {
        return curriculumService.listLessons(moduleId);
    }

    @Operation(summary = "Add a lesson",
            description = "Notifies batch students when the course is already published (Doc S16).")
    @PreAuthorize(Roles.ACADEMIC)
    @PostMapping("/api/modules/{moduleId}/lessons")
    public ResponseEntity<LessonResponse> addLesson(@PathVariable Long moduleId,
                                                    @Valid @RequestBody LessonRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(curriculumService.addLesson(moduleId, request));
    }

    @Operation(summary = "Update a lesson")
    @PreAuthorize(Roles.ACADEMIC)
    @PutMapping("/api/lessons/{lessonId}")
    public LessonResponse updateLesson(@PathVariable Long lessonId,
                                       @Valid @RequestBody LessonRequest request) {
        return curriculumService.updateLesson(lessonId, request);
    }

    @Operation(summary = "Delete a lesson",
            description = "Refused once any student has completed it. Mark it optional instead.")
    @PreAuthorize(Roles.ACADEMIC)
    @DeleteMapping("/api/lessons/{lessonId}")
    public ApiMessage deleteLesson(@PathVariable Long lessonId) {
        curriculumService.deleteLesson(lessonId);
        return ApiMessage.ok("Lesson deleted");
    }

    @Operation(summary = "Reorder a module's lessons")
    @PreAuthorize(Roles.ACADEMIC)
    @PutMapping("/api/modules/{moduleId}/lessons/order")
    public ApiMessage reorderLessons(@PathVariable Long moduleId, @RequestBody List<Long> orderedIds) {
        curriculumService.reorderLessons(moduleId, orderedIds);
        return ApiMessage.ok("Lessons reordered");
    }
}
