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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.itilms.admission.dto.request.AddFollowupRequest;
import com.itilms.admission.dto.request.ConvertLeadRequest;
import com.itilms.admission.dto.request.CreateLeadRequest;
import com.itilms.admission.dto.request.PublicEnquiryRequest;
import com.itilms.admission.dto.request.UpdateLeadRequest;
import com.itilms.admission.dto.response.AdmissionResultResponse;
import com.itilms.admission.dto.response.FollowupResponse;
import com.itilms.admission.dto.response.LeadResponse;
import com.itilms.admission.service.LeadService;
import com.itilms.common.dto.PageResponse;
import com.itilms.common.security.AppPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** The enquiry pipeline, from website form to admission (Doc S6.4, S7.1). */
@Tag(name = "Leads", description = "Enquiries, follow-ups and admissions")
@RestController
@RequestMapping("/api/leads")
@RequiredArgsConstructor
public class LeadController {

    private final LeadService leadService;

    @Operation(summary = "Submit a website enquiry",
            description = "Public. The only anonymous write in the whole system, and the reason "
                    + "the gateway rate-limits this path.")
    @SecurityRequirements
    @PostMapping("/enquiry")
    public ResponseEntity<LeadResponse> enquiry(@Valid @RequestBody PublicEnquiryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(leadService.captureEnquiry(request));
    }

    @Operation(summary = "List leads",
            description = "Defaults to open leads only. Counselors normally filter to their own.")
    @PreAuthorize("hasAnyRole('ADMIN','COORDINATOR','PLACEMENT')")
    @GetMapping
    public PageResponse<LeadResponse> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) Long counselorUserId,
            @RequestParam(defaultValue = "false") boolean openOnly,
            @RequestParam(required = false) String query,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return leadService.search(status, source, counselorUserId, openOnly, query, pageable);
    }

    @Operation(summary = "My overdue follow-ups",
            description = "Leads whose call-back date has passed. The counselor's daily worklist.")
    @PreAuthorize("hasAnyRole('ADMIN','COORDINATOR','PLACEMENT')")
    @GetMapping("/my-overdue")
    public List<LeadResponse> myOverdue(@AuthenticationPrincipal AppPrincipal principal) {
        return leadService.overdueFollowUps(principal.userId());
    }

    @Operation(summary = "All overdue follow-ups", description = "Across every counselor.")
    @PreAuthorize("hasAnyRole('ADMIN','COORDINATOR')")
    @GetMapping("/overdue")
    public List<LeadResponse> allOverdue() {
        return leadService.overdueFollowUps(null);
    }

    @Operation(summary = "Conversion funnel", description = "Lead counts per pipeline stage.")
    @PreAuthorize("hasAnyRole('ADMIN','COORDINATOR','PLACEMENT')")
    @GetMapping("/stats/funnel")
    public Map<String, Long> funnel() {
        return leadService.funnel();
    }

    @Operation(summary = "Get a lead")
    @PreAuthorize("hasAnyRole('ADMIN','COORDINATOR','PLACEMENT')")
    @GetMapping("/{id}")
    public LeadResponse get(@PathVariable Long id) {
        return leadService.get(id);
    }

    @Operation(summary = "Create a lead", description = "For walk-ins, phone calls and referrals.")
    @PreAuthorize("hasAnyRole('ADMIN','COORDINATOR','PLACEMENT')")
    @PostMapping
    public ResponseEntity<LeadResponse> create(@Valid @RequestBody CreateLeadRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(leadService.create(request));
    }

    @Operation(summary = "Update a lead",
            description = "The status cannot be set to CONVERTED here - use the convert endpoint.")
    @PreAuthorize("hasAnyRole('ADMIN','COORDINATOR','PLACEMENT')")
    @PutMapping("/{id}")
    public LeadResponse update(@PathVariable Long id, @Valid @RequestBody UpdateLeadRequest request) {
        return leadService.update(id, request);
    }

    @Operation(summary = "Log a follow-up",
            description = "Records the attempt and schedules the next call-back.")
    @PreAuthorize("hasAnyRole('ADMIN','COORDINATOR','PLACEMENT')")
    @PostMapping("/{id}/followups")
    public ResponseEntity<FollowupResponse> addFollowup(@PathVariable Long id,
                                                        @Valid @RequestBody AddFollowupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(leadService.addFollowup(id, request));
    }

    @Operation(summary = "Follow-up history")
    @PreAuthorize("hasAnyRole('ADMIN','COORDINATOR','PLACEMENT')")
    @GetMapping("/{id}/followups")
    public List<FollowupResponse> followups(@PathVariable Long id) {
        return leadService.followups(id);
    }

    @Operation(summary = "Admit this lead",
            description = "Creates the account and student profile, records the conversion, and "
                    + "triggers the fee plan and welcome message. Doc S7.1 in one call.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Admitted"),
            @ApiResponse(responseCode = "422", description = "Already converted, or no email on the lead"),
            @ApiResponse(responseCode = "503", description = "The account service is unavailable; nothing was created")
    })
    @PreAuthorize("hasAnyRole('ADMIN','COORDINATOR','PLACEMENT')")
    @PostMapping("/{id}/convert")
    public ResponseEntity<AdmissionResultResponse> convert(@PathVariable Long id,
                                                           @Valid @RequestBody ConvertLeadRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(leadService.convert(id, request));
    }
}
