package com.itilms.batch.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.itilms.batch.dto.request.CourseRequestDtos.ApproveRequest;
import com.itilms.batch.dto.request.CourseRequestDtos.CourseRequestResponse;
import com.itilms.batch.dto.request.CourseRequestDtos.CreateRequest;
import com.itilms.batch.dto.request.CourseRequestDtos.RejectRequest;
import com.itilms.batch.entity.CourseRequestStatus;
import com.itilms.batch.service.CourseRequestService;
import com.itilms.common.dto.PageResponse;
import com.itilms.common.security.Roles;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/course-requests")
@Tag(name = "Course requests", description = "A student asks to join a course; an administrator or coordinator decides")
@RequiredArgsConstructor
public class CourseRequestController {

    private final CourseRequestService service;

    @Operation(summary = "Ask to join a course", description = "Students only. One open request per course.")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping
    public ResponseEntity<CourseRequestResponse> create(@Valid @RequestBody CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @Operation(summary = "My requests", description = "Every request the signed-in student has made, newest first.")
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/mine")
    public List<CourseRequestResponse> mine() {
        return service.mine();
    }

    @Operation(summary = "Withdraw my request", description = "Only while it is still waiting.")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping("/{id}/cancel")
    public CourseRequestResponse cancel(@PathVariable Long id) {
        return service.cancel(id);
    }

    @Operation(summary = "Requests waiting for a decision", description = "Administrators and coordinators. Filter by status.")
    @PreAuthorize(Roles.STAFF)
    @GetMapping
    public PageResponse<CourseRequestResponse> list(@RequestParam(required = false) CourseRequestStatus status,
                                                    @PageableDefault(size = 20) Pageable pageable) {
        return service.list(status, pageable);
    }

    @Operation(summary = "One request", description = "The student who made it, or staff.")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public CourseRequestResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @Operation(summary = "Approve", description = "Enrols the student in a batch of the requested course.")
    @PreAuthorize(Roles.STAFF)
    @PostMapping("/{id}/approve")
    public CourseRequestResponse approve(@PathVariable Long id, @Valid @RequestBody ApproveRequest body) {
        return service.approve(id, body);
    }

    @Operation(summary = "Reject", description = "A reason is required; the student sees it.")
    @PreAuthorize(Roles.STAFF)
    @PostMapping("/{id}/reject")
    public CourseRequestResponse reject(@PathVariable Long id, @Valid @RequestBody RejectRequest body) {
        return service.reject(id, body);
    }
}
