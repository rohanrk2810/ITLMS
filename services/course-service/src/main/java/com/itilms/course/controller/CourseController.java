package com.itilms.course.controller;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.itilms.common.dto.PageResponse;
import com.itilms.common.security.Roles;
import com.itilms.course.dto.request.CreateCourseRequest;
import com.itilms.course.dto.request.UpdateCourseRequest;
import com.itilms.course.dto.response.CourseDetailResponse;
import com.itilms.course.dto.response.CourseResponse;
import com.itilms.course.dto.response.CourseSummaryResponse;
import com.itilms.course.service.CourseService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * The course catalog (Doc S6.5, S8.1, S11).
 *
 * <p>The {@code /public/**} endpoints are anonymous and serve the marketing site.
 * They are a separate path rather than the same endpoints with an optional
 * token, so the "published only" filter is structural: there is no parameter an
 * anonymous caller can send to reach an unpublished course.
 */
@Tag(name = "Courses", description = "Course catalog")
@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    // -----------------------------------------------------------------
    // Public
    // -----------------------------------------------------------------

    @Operation(summary = "Browse the public catalog",
            description = "Published courses only. No authentication required.")
    @SecurityRequirements
    @GetMapping("/public")
    public PageResponse<CourseSummaryResponse> publicCatalog(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String query,
            @PageableDefault(size = 12, sort = "title") Pageable pageable) {
        return courseService.publicCatalog(level, query, pageable);
    }

    @Operation(summary = "Public course page",
            description = "Curriculum outline with lesson titles. Lesson material is withheld "
                    + "unless the lesson is flagged as a preview.")
    @SecurityRequirements
    @GetMapping("/public/{id}")
    public CourseDetailResponse publicDetail(@PathVariable Long id) {
        return courseService.getDetail(id);
    }

    // -----------------------------------------------------------------
    // Authenticated
    // -----------------------------------------------------------------

    @Operation(summary = "List courses", description = "Staff view across every status.")
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public PageResponse<CourseSummaryResponse> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String query,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return courseService.search(status, level, query, pageable);
    }

    @Operation(summary = "Course with curriculum",
            description = "Lesson content is included for staff and for enrolled students; "
                    + "everyone else receives the outline with material withheld.")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public CourseDetailResponse detail(@PathVariable Long id) {
        return courseService.getDetail(id);
    }

    @Operation(summary = "Create a course", description = "Starts in DRAFT and is not yet visible.")
    @PreAuthorize(Roles.STAFF)
    @PostMapping
    public ResponseEntity<CourseResponse> create(@Valid @RequestBody CreateCourseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(courseService.create(request));
    }

    @Operation(summary = "Update a course")
    @PreAuthorize(Roles.STAFF)
    @PutMapping("/{id}")
    public CourseResponse update(@PathVariable Long id, @Valid @RequestBody UpdateCourseRequest request) {
        return courseService.update(id, request);
    }

    @Operation(summary = "Publish a course",
            description = "Makes it visible to students. Refused unless the summary, description "
                    + "and at least one mandatory lesson are in place (Doc S14).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Published"),
            @ApiResponse(responseCode = "422", description = "Required metadata or content is missing")
    })
    @PreAuthorize(Roles.STAFF)
    @PostMapping("/{id}/publish")
    public CourseResponse publish(@PathVariable Long id) {
        return courseService.publish(id);
    }

    @Operation(summary = "Archive a course",
            description = "Removes it from the catalog. Existing students keep their content, "
                    + "results and certificates.")
    @PreAuthorize(Roles.ADMIN_ONLY)
    @PostMapping("/{id}/archive")
    public CourseResponse archive(@PathVariable Long id) {
        return courseService.archive(id);
    }

    @Operation(summary = "Course counts by status")
    @PreAuthorize(Roles.STAFF)
    @GetMapping("/stats/counts")
    public Map<String, Long> counts() {
        return courseService.counts();
    }

    @Operation(summary = "Resolve many course ids", description = "Internal bulk lookup.")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/internal/lookup")
    public List<CourseSummaryResponse> lookup(@RequestBody List<Long> courseIds) {
        return courseService.findByIds(courseIds);
    }
}
