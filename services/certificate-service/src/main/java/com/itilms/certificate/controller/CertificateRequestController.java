package com.itilms.certificate.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
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

import com.itilms.certificate.dto.response.CertificateRequestDetailResponse;
import com.itilms.certificate.dto.response.CertificateRequestResponse;
import com.itilms.certificate.dto.response.CertificateResponse;
import com.itilms.certificate.entity.CertificateRequestStatus;
import com.itilms.certificate.service.CertificateRequestService;
import com.itilms.common.dto.PageResponse;
import com.itilms.common.security.Roles;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

/**
 * Certificate requests. A student asks; only an ADMIN decides. The service repeats every check
 * itself, so the annotations here are a first gate, not the only one.
 */
@Tag(name = "Certificate requests", description = "Request, approve or reject, and issue")
@RestController
@RequestMapping("/api/certificates/requests")
@RequiredArgsConstructor
public class CertificateRequestController {

    private final CertificateRequestService service;

    @Operation(summary = "Request a certificate",
            description = "Only for a course the student is eligible for, and only one open request per course.")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping
    public ResponseEntity<CertificateRequestResponse> request(@Valid @RequestBody CourseBody body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.request(body.courseId()));
    }

    @Operation(summary = "My certificate requests")
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/me")
    public List<CertificateRequestResponse> mine() {
        return service.mine();
    }

    @Operation(summary = "Search requests",
            description = "Admin. A trainer only when the institute has enabled trainer access.")
    @PreAuthorize("hasAnyRole('ADMIN','TRAINER')")
    @GetMapping
    public PageResponse<CertificateRequestResponse> search(
            @RequestParam(required = false) List<CertificateRequestStatus> status,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.search(status, courseId, batchId, q, from, to, pageable);
    }

    @Operation(summary = "One request, with eligibility re-checked now")
    @PreAuthorize("hasAnyRole('ADMIN','TRAINER','STUDENT')")
    @GetMapping("/{id}")
    public CertificateRequestDetailResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @Operation(summary = "Approve a request (admin)")
    @PreAuthorize(Roles.ADMIN_ONLY)
    @PostMapping("/{id}/approve")
    public CertificateRequestResponse approve(@PathVariable Long id) {
        return service.approve(id);
    }

    @Operation(summary = "Reject a request (admin)", description = "A reason is required and is shown to the student.")
    @PreAuthorize(Roles.ADMIN_ONLY)
    @PostMapping("/{id}/reject")
    public CertificateRequestResponse reject(@PathVariable Long id, @Valid @RequestBody RejectBody body) {
        return service.reject(id, body.reason());
    }

    @Operation(summary = "Issue the certificate for an approved request (admin)")
    @PreAuthorize(Roles.ADMIN_ONLY)
    @PostMapping("/{id}/issue")
    public ResponseEntity<CertificateResponse> issue(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.issue(id));
    }

    public record CourseBody(@NotNull(message = "Course is required") Long courseId) {
    }

    public record RejectBody(@NotBlank(message = "A reason is required") String reason) {
    }
}
