package com.itilms.certificate.controller;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.itilms.certificate.dto.request.IssueCertificateRequest;
import com.itilms.certificate.dto.response.CertificateResponse;
import com.itilms.certificate.dto.response.EligibilityResponse;
import com.itilms.certificate.dto.response.VerificationResponse;
import com.itilms.certificate.service.CertificateService;
import com.itilms.common.dto.PageResponse;
import com.itilms.common.security.Roles;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

/** Certificates (Doc S6.13, S7.3, S11). */
@Tag(name = "Certificates", description = "Eligibility, issue, download and public verification")
@RestController
@RequestMapping("/api/certificates")
@RequiredArgsConstructor
public class CertificateController {

    private final CertificateService certificateService;

    @Operation(summary = "Is this student eligible?",
            description = "Each condition of the completion rule (Doc S7.3), checked now against the "
                    + "service that owns it. A condition that could not be checked is never treated as met.")
    @PreAuthorize("hasAnyRole('ADMIN','COORDINATOR','STUDENT')")
    @GetMapping("/eligibility")
    public EligibilityResponse eligibility(@RequestParam Long studentId, @RequestParam Long courseId) {
        return certificateService.eligibility(studentId, courseId);
    }

    @Operation(summary = "Issue a certificate",
            description = "Every condition is checked again at the moment of issue (Doc S14).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Issued"),
            @ApiResponse(responseCode = "409", description = "Already holds one for this course"),
            @ApiResponse(responseCode = "422", description = "Not eligible; the message says why")
    })
    @PreAuthorize(Roles.STAFF)
    @PostMapping
    public ResponseEntity<CertificateResponse> issue(@Valid @RequestBody IssueCertificateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(certificateService.issue(request.studentId(), request.courseId()));
    }

    @Operation(summary = "Claim my certificate",
            description = "For a student who has met every condition. Same checks as a staff issue.")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping("/claim")
    public ResponseEntity<CertificateResponse> claim(@Valid @RequestBody IssueCertificateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(certificateService.claim(request.courseId()));
    }

    @Operation(summary = "My certificates")
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/me")
    public List<CertificateResponse> mine() {
        return certificateService.mine();
    }

    @Operation(summary = "List certificates")
    @PreAuthorize(Roles.STAFF)
    @GetMapping
    public PageResponse<CertificateResponse> list(@RequestParam(required = false) Long courseId,
                                                  @PageableDefault(size = 20) Pageable pageable) {
        return certificateService.list(courseId, pageable);
    }

    @Operation(summary = "One certificate", description = "A student may see only their own.")
    @PreAuthorize("hasAnyRole('ADMIN','COORDINATOR','STUDENT')")
    @GetMapping("/{id}")
    public CertificateResponse get(@PathVariable Long id) {
        return certificateService.get(id);
    }

    @Operation(summary = "Download the certificate as PDF")
    @PreAuthorize("hasAnyRole('ADMIN','COORDINATOR','STUDENT')")
    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        byte[] body = certificateService.pdf(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("certificate-" + id + ".pdf").build().toString())
                // A certificate PDF names a person; it should not sit in a shared cache.
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentType(MediaType.APPLICATION_PDF)
                .body(body);
    }

    @Operation(summary = "Revoke a certificate",
            description = "Admin only. The certificate stays on record and verifies as REVOKED.")
    @PreAuthorize(Roles.ADMIN_ONLY)
    @PostMapping("/{id}/revoke")
    public CertificateResponse revoke(@PathVariable Long id, @Valid @RequestBody RevokeRequest request) {
        return certificateService.revoke(id, request.reason());
    }

    @Operation(summary = "Verify a certificate (public)",
            description = "Needs the number and the verification code printed on the certificate. Shows "
                    + "only the name, course, date and whether it is still valid. Rate-limited.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Found"),
            @ApiResponse(responseCode = "404", description = "No certificate matches that number and code")
    })
    @GetMapping("/verify/{certificateNo}")
    public VerificationResponse verify(@PathVariable String certificateNo,
                                       @RequestParam(required = false) String code) {
        return certificateService.verify(certificateNo, code);
    }

    public record RevokeRequest(@NotBlank(message = "A reason is required") String reason) {
    }
}
