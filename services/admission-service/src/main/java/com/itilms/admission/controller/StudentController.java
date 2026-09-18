package com.itilms.admission.controller;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.itilms.admission.dto.request.CreateStudentRequest;
import com.itilms.admission.dto.request.UpdateStudentRequest;
import com.itilms.admission.dto.response.StudentResponse;
import com.itilms.admission.dto.response.StudentSummaryResponse;
import com.itilms.admission.entity.LeadSource;
import com.itilms.admission.service.StudentService;
import com.itilms.common.dto.PageResponse;
import com.itilms.common.security.AppPrincipal;
import com.itilms.common.security.Roles;
import com.itilms.common.security.SecurityUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Student profiles (Doc S6.2, S11).
 *
 * <p>Two access patterns live here and they are kept apart deliberately:
 * {@code /me} endpoints can only ever return the caller's own record, while
 * {@code /{id}} endpoints are staff-only. A student hitting {@code /{id}} with
 * someone else's id gets 403 from the role check, not a filtered result — there
 * is no path by which a student reaches another student's profile.
 */
@Tag(name = "Students", description = "Student profiles and admissions")
@RestController
@RequestMapping("/api/students")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService studentService;

    @Operation(summary = "List students", description = "Staff view with search and status filter.")
    @PreAuthorize("hasAnyRole('ADMIN','COORDINATOR','PLACEMENT','FINANCE')")
    @GetMapping
    public PageResponse<StudentSummaryResponse> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String query,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return studentService.search(status, query, pageable);
    }

    @Operation(summary = "My profile", description = "The signed-in student's own record.")
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/me")
    public StudentResponse me(@AuthenticationPrincipal AppPrincipal principal) {
        return studentService.getByUserId(principal.userId());
    }

    @Operation(summary = "Update my profile",
            description = "A student may correct their own address, guardian and education details. "
                    + "Name, email and phone are changed through the account, not here.")
    @PreAuthorize("hasRole('STUDENT')")
    @PutMapping("/me")
    public StudentResponse updateMe(@AuthenticationPrincipal AppPrincipal principal,
                                    @Valid @RequestBody UpdateStudentRequest request) {
        StudentResponse mine = studentService.getByUserId(principal.userId());
        return studentService.update(mine.id(), request);
    }

    @Operation(summary = "Get a student")
    @PreAuthorize("hasAnyRole('ADMIN','COORDINATOR','PLACEMENT','FINANCE','TRAINER')")
    @GetMapping("/{id}")
    public StudentResponse get(@PathVariable Long id) {
        return studentService.get(id);
    }

    @Operation(summary = "Admit a student directly",
            description = "Creates the login account and the profile in one step. Use this for "
                    + "walk-in admissions with no prior enquiry; otherwise convert the lead.")
    @PreAuthorize(Roles.STAFF)
    @PostMapping
    public ResponseEntity<StudentResponse> create(@Valid @RequestBody CreateStudentRequest request) {
        StudentResponse created = studentService.create(
                request, LeadSource.WALK_IN, StudentService.AdmissionTerms.none());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @Operation(summary = "Update a student profile")
    @PreAuthorize(Roles.STAFF)
    @PutMapping("/{id}")
    public StudentResponse update(@PathVariable Long id,
                                  @Valid @RequestBody UpdateStudentRequest request) {
        return studentService.update(id, request);
    }

    @Operation(summary = "Change a student's standing",
            description = "ACTIVE, ALUMNI, DROPPED or SUSPENDED. Separate from the account status, "
                    + "so an alumnus keeps signing in to download their certificate.")
    @PreAuthorize(Roles.STAFF)
    @PatchMapping("/{id}/status")
    public StudentResponse updateStatus(@PathVariable Long id,
                                        @RequestParam String status,
                                        @RequestParam(required = false) String reason) {
        return studentService.updateStatus(id, status, reason);
    }

    @Operation(summary = "Student counts by standing", description = "Feeds the admin dashboard.")
    @PreAuthorize("hasAnyRole('ADMIN','COORDINATOR','FINANCE','PLACEMENT')")
    @GetMapping("/stats/counts")
    public Map<String, Long> counts() {
        return studentService.counts();
    }

    @Operation(summary = "Resolve many student ids",
            description = "Internal bulk lookup for batch rosters, fee reports and attendance sheets.")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/internal/lookup")
    public List<StudentSummaryResponse> lookup(@RequestBody List<Long> studentIds) {
        // A student calling this could otherwise read the roster of any batch.
        // Restricting them to their own id keeps the endpoint useful to services
        // without turning it into a directory for anyone holding a token.
        var principal = SecurityUtils.requirePrincipal();
        if (principal.isStudent()) {
            return studentService.findByIds(List.of(principal.profileId()));
        }
        return studentService.findByIds(studentIds);
    }
}
