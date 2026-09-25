package com.itilms.course.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.itilms.common.exception.ForbiddenOperationException;
import com.itilms.common.security.AppPrincipal;
import com.itilms.common.security.Roles;
import com.itilms.course.dto.request.LessonProgressRequest;
import com.itilms.course.dto.response.ProgressResponse;
import com.itilms.course.dto.response.StudentCourseProgressResponse;
import com.itilms.course.service.ProgressService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Lesson progress (Doc S7.2). */
@Tag(name = "Progress", description = "Lesson progress and course completion")
@RestController
@RequestMapping("/api/progress")
@RequiredArgsConstructor
public class ProgressController {

    private final ProgressService progressService;

    @Operation(summary = "Record progress on a lesson",
            description = "Send watchedSeconds while a video plays, and completed=true when the "
                    + "student finishes. The enrolment is resolved from the caller's own profile.")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping("/lessons/{lessonId}")
    public ProgressResponse record(@PathVariable Long lessonId,
                                   @Valid @RequestBody LessonProgressRequest request) {
        return progressService.recordProgress(lessonId, request);
    }

    @Operation(summary = "My progress", description = "Every course the signed-in student is taking.")
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/me")
    public List<ProgressResponse> myProgress(@AuthenticationPrincipal AppPrincipal principal) {
        if (principal.profileId() == null) {
            throw new ForbiddenOperationException(
                    "Your account is not yet linked to a student profile. Contact the institute office.");
        }
        return progressService.myProgress(principal.profileId());
    }

    @Operation(summary = "Internal: every course one student is taking, module by module",
            description = "For reporting-service's student progress report. Not reachable through the gateway; "
                    + "reporting-service decides who may see which student.")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/internal/students/{studentId}")
    public List<StudentCourseProgressResponse> courseProgressOf(@PathVariable Long studentId) {
        return progressService.courseProgressOf(studentId);
    }

    @Operation(summary = "Progress for a batch",
            description = "The trainer's view of how their class is getting on.")
    @PreAuthorize(Roles.ACADEMIC)
    @GetMapping("/batches/{batchId}")
    public List<ProgressResponse> batchProgress(@PathVariable Long batchId) {
        return progressService.batchProgress(batchId);
    }

    @Operation(summary = "Progress for one student on one course",
            description = "Used by certificate-service to check the lesson condition from Doc S7.3.")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/students/{studentId}/courses/{courseId}")
    public ProgressResponse forStudent(@AuthenticationPrincipal AppPrincipal principal,
                                       @PathVariable Long studentId,
                                       @PathVariable Long courseId) {
        // A student asking about someone else gets 403, not a filtered answer.
        if (principal.isStudent() && !studentId.equals(principal.profileId())) {
            throw new ForbiddenOperationException("You may only view your own progress");
        }
        return progressService.progressFor(studentId, courseId);
    }
}
