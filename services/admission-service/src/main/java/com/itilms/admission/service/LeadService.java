package com.itilms.admission.service;

import java.util.List;

import org.springframework.data.domain.Pageable;

import com.itilms.admission.dto.request.AddFollowupRequest;
import com.itilms.admission.dto.request.ConvertLeadRequest;
import com.itilms.admission.dto.request.CreateLeadRequest;
import com.itilms.admission.dto.request.PublicEnquiryRequest;
import com.itilms.admission.dto.request.UpdateLeadRequest;
import com.itilms.admission.dto.response.AdmissionResultResponse;
import com.itilms.admission.dto.response.FollowupResponse;
import com.itilms.admission.dto.response.LeadResponse;
import com.itilms.common.dto.PageResponse;

/** The enquiry-to-admission pipeline (Doc S6.4, S7.1). */
public interface LeadService {

    /** Anonymous website enquiry. Creates an unassigned lead. */
    LeadResponse captureEnquiry(PublicEnquiryRequest request);

    LeadResponse create(CreateLeadRequest request);

    PageResponse<LeadResponse> search(String status, String source, Long counselorUserId,
                                      boolean openOnly, String query, Pageable pageable);

    LeadResponse get(Long id);

    LeadResponse update(Long id, UpdateLeadRequest request);

    FollowupResponse addFollowup(Long leadId, AddFollowupRequest request);

    List<FollowupResponse> followups(Long leadId);

    /**
     * Turns the lead into an admitted student.
     *
     * <p>Creates the account and profile, records the conversion, and announces
     * the admission so finance raises a fee plan and batch-service enrols the
     * student. Rejected if the lead was already converted.
     */
    AdmissionResultResponse convert(Long leadId, ConvertLeadRequest request);

    /** Follow-ups whose date has passed — the counselor's daily worklist. */
    List<LeadResponse> overdueFollowUps(Long counselorUserId);

    /** Conversion funnel counts for the dashboard. */
    java.util.Map<String, Long> funnel();
}
