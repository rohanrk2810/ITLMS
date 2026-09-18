package com.itilms.admission.service.impl;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.admission.dto.request.AddFollowupRequest;
import com.itilms.admission.dto.request.ConvertLeadRequest;
import com.itilms.admission.dto.request.CreateLeadRequest;
import com.itilms.admission.dto.request.CreateStudentRequest;
import com.itilms.admission.dto.request.PublicEnquiryRequest;
import com.itilms.admission.dto.request.UpdateLeadRequest;
import com.itilms.admission.dto.response.AdmissionResultResponse;
import com.itilms.admission.dto.response.FollowupResponse;
import com.itilms.admission.dto.response.LeadResponse;
import com.itilms.admission.entity.FollowupOutcome;
import com.itilms.admission.entity.Lead;
import com.itilms.admission.entity.LeadFollowup;
import com.itilms.admission.entity.LeadSource;
import com.itilms.admission.entity.LeadStatus;
import com.itilms.admission.repository.LeadFollowupRepository;
import com.itilms.admission.repository.LeadRepository;
import com.itilms.admission.service.LeadService;
import com.itilms.admission.service.StudentService;
import com.itilms.admission.specification.LeadSpecifications;
import com.itilms.common.dto.PageResponse;
import com.itilms.common.event.EventPublisher;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.common.security.SecurityUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeadServiceImpl implements LeadService {

    private static final String SERVICE_NAME = "admission-service";

    private final LeadRepository leadRepository;
    private final LeadFollowupRepository followupRepository;
    private final StudentService studentService;
    private final EventPublisher events;

    // -----------------------------------------------------------------
    // Capture
    // -----------------------------------------------------------------

    @Override
    @Transactional
    public LeadResponse captureEnquiry(PublicEnquiryRequest request) {
        // No counselor is assigned: this arrives from the public site with
        // nobody attached, and it lands in the unassigned queue for the
        // admissions desk to pick up. Auto-assigning round-robin would be
        // tempting but produces silent drops when someone is on leave.
        Lead lead = leadRepository.save(Lead.builder()
                .name(request.name().trim())
                .phone(request.phone().trim())
                .email(trim(request.email()))
                .source(LeadSource.WEBSITE)
                .interestedCourseId(request.interestedCourseId())
                .status(LeadStatus.NEW)
                .notes(trim(request.message()))
                .build());

        log.info("Captured website enquiry {} from {}", lead.getId(), lead.getPhone());

        // Tell the admissions desk something is waiting.
        events.publishAfterCommit(com.itilms.common.event.KafkaTopics.NOTIFICATION_REQUESTED,
                new com.itilms.common.event.NotificationRequestedEvent(
                        com.itilms.common.event.DomainEvent.newId(), Instant.now(),
                        List.of(), null, "PLACEMENT",
                        "NEW_ENQUIRY", "New website enquiry",
                        "%s (%s) enquired through the website.".formatted(lead.getName(), lead.getPhone()),
                        "/admin/leads/" + lead.getId(), false, Map.of()));

        return LeadResponse.from(lead);
    }

    @Override
    @Transactional
    public LeadResponse create(CreateLeadRequest request) {
        // Whoever records the lead owns it unless they say otherwise. An
        // unowned lead is one nobody chases.
        Long counselor = request.counselorUserId() != null
                ? request.counselorUserId()
                : SecurityUtils.currentUserId();

        Lead lead = leadRepository.save(Lead.builder()
                .name(request.name().trim())
                .phone(request.phone().trim())
                .email(trim(request.email()))
                .source(parseSource(request.source()))
                .interestedCourseId(request.interestedCourseId())
                .counselorUserId(counselor)
                .status(LeadStatus.NEW)
                .nextFollowUpAt(request.nextFollowUpAt())
                .notes(trim(request.notes()))
                .build());

        events.audit(SERVICE_NAME, "LEAD_CREATED", "Lead", lead.getId(), null,
                Map.of("name", lead.getName(), "source", lead.getSource().name()));

        return LeadResponse.from(lead);
    }

    // -----------------------------------------------------------------
    // Read
    // -----------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public PageResponse<LeadResponse> search(String status, String source, Long counselorUserId,
                                             boolean openOnly, String query, Pageable pageable) {
        Specification<Lead> spec = Specification.allOf(
                LeadSpecifications.hasStatus(status),
                LeadSpecifications.hasSource(source),
                LeadSpecifications.assignedTo(counselorUserId),
                LeadSpecifications.openOnly(openOnly),
                LeadSpecifications.matches(query));

        return PageResponse.from(leadRepository.findAll(spec, pageable), LeadResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public LeadResponse get(Long id) {
        Lead lead = leadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lead", id));
        return LeadResponse.from(lead, followupRepository.countByLeadId(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<FollowupResponse> followups(Long leadId) {
        if (!leadRepository.existsById(leadId)) {
            throw new ResourceNotFoundException("Lead", leadId);
        }
        return followupRepository.findByLeadIdOrderByContactedAtDesc(leadId)
                .stream().map(FollowupResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeadResponse> overdueFollowUps(Long counselorUserId) {
        return leadRepository.findOverdueFollowUps(Instant.now(), counselorUserId)
                .stream().map(LeadResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> funnel() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (LeadStatus status : LeadStatus.values()) {
            counts.put(status.name(), 0L);
        }
        leadRepository.countGroupedByStatus()
                .forEach(row -> counts.put(((LeadStatus) row[0]).name(), (Long) row[1]));
        counts.put("TOTAL", counts.values().stream().mapToLong(Long::longValue).sum());
        return counts;
    }

    // -----------------------------------------------------------------
    // Update
    // -----------------------------------------------------------------

    @Override
    @Transactional
    public LeadResponse update(Long id, UpdateLeadRequest request) {
        Lead lead = leadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lead", id));

        if (lead.isConverted()) {
            throw new BusinessRuleException(
                    "This lead has already been admitted. Edit the student profile instead.");
        }

        LeadStatus target = parseStatus(request.status());
        if (target == LeadStatus.CONVERTED) {
            // Conversion creates an account, a profile and a fee plan. Allowing
            // it to happen by setting a field would leave a lead marked
            // converted with no student behind it.
            throw new BusinessRuleException(
                    "Use POST /api/leads/{id}/convert to admit this lead; the status cannot be set directly.");
        }
        if ((target == LeadStatus.LOST || target == LeadStatus.NOT_INTERESTED)
                && (request.lostReason() == null || request.lostReason().isBlank())) {
            throw new BusinessRuleException(
                    "Record why this lead was lost - it is what makes the source report worth reading.");
        }

        LeadStatus previous = lead.getStatus();

        lead.setName(request.name().trim());
        lead.setPhone(request.phone().trim());
        lead.setEmail(trim(request.email()));
        lead.setSource(parseSource(request.source()));
        lead.setStatus(target);
        lead.setInterestedCourseId(request.interestedCourseId());
        lead.setCounselorUserId(request.counselorUserId());
        lead.setNotes(trim(request.notes()));
        lead.setLostReason(trim(request.lostReason()));
        // A closed lead should not sit in anyone's follow-up queue.
        lead.setNextFollowUpAt(target.isClosed() ? null : request.nextFollowUpAt());

        leadRepository.save(lead);

        if (previous != target) {
            events.audit(SERVICE_NAME, "LEAD_STATUS_CHANGED", "Lead", id,
                    Map.of("status", previous.name()), Map.of("status", target.name()));
        }

        return LeadResponse.from(lead, followupRepository.countByLeadId(id));
    }

    @Override
    @Transactional
    public FollowupResponse addFollowup(Long leadId, AddFollowupRequest request) {
        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new ResourceNotFoundException("Lead", leadId));

        if (lead.isConverted()) {
            throw new BusinessRuleException("This lead has already been admitted");
        }

        LeadFollowup followup = followupRepository.save(LeadFollowup.builder()
                .leadId(leadId)
                .outcome(parseOutcome(request.outcome()))
                .remark(trim(request.remark()))
                .nextActionAt(request.nextActionAt())
                .createdBy(SecurityUtils.currentUserId())
                .build());

        // The lead's own follow-up date mirrors the latest attempt, so the
        // worklist query stays a single indexed lookup rather than a join
        // against the history table on every page load.
        lead.setNextFollowUpAt(request.nextActionAt());

        if (request.newStatus() != null && !request.newStatus().isBlank()) {
            LeadStatus target = parseStatus(request.newStatus());
            if (target == LeadStatus.CONVERTED) {
                throw new BusinessRuleException(
                        "Use POST /api/leads/{id}/convert to admit this lead");
            }
            lead.setStatus(target);
            if (target.isClosed()) {
                lead.setNextFollowUpAt(null);
            }
        } else if (lead.getStatus() == LeadStatus.NEW) {
            // Somebody has now actually spoken to them.
            lead.setStatus(LeadStatus.CONTACTED);
        }

        leadRepository.save(lead);
        return FollowupResponse.from(followup);
    }

    // -----------------------------------------------------------------
    // Convert
    // -----------------------------------------------------------------

    @Override
    @Transactional
    public AdmissionResultResponse convert(Long leadId, ConvertLeadRequest request) {
        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new ResourceNotFoundException("Lead", leadId));

        if (lead.isConverted()) {
            throw new BusinessRuleException(
                    "This lead was already admitted as student %d".formatted(lead.getConvertedStudentId()));
        }
        if (lead.getEmail() == null || lead.getEmail().isBlank()) {
            // The account is keyed on email and the temporary password is sent
            // there, so an admission without one would create an account the
            // student can never get into.
            throw new BusinessRuleException(
                    "Add an email address to this lead before admitting them - "
                            + "their account and password are sent to it.");
        }

        var discount = request.discountOrZero();
        if (discount.compareTo(request.totalFee()) > 0) {
            throw new BusinessRuleException("The discount cannot exceed the total fee");
        }

        var name = splitName(lead.getName());
        var studentRequest = new CreateStudentRequest(
                name.first(), name.last(), lead.getEmail(), lead.getPhone(),
                null, null, null, null, null,
                null, null, null, null,
                null, null, null,
                request.admissionDateOrToday(), buildRemark(lead));

        var terms = new StudentService.AdmissionTerms(
                request.courseId(), request.batchId(),
                request.totalFee(), discount, request.installmentsOrOne());

        var student = studentService.create(studentRequest, lead.getSource(), terms);

        lead.markConverted(student.id());
        leadRepository.save(lead);

        events.audit(SERVICE_NAME, "LEAD_CONVERTED", "Lead", leadId,
                Map.of("status", LeadStatus.CONVERTED.name()),
                Map.of("studentId", student.id(), "courseId", request.courseId(),
                        "totalFee", request.totalFee(), "discount", discount));

        log.info("Lead {} admitted as student {} ({}) on course {}",
                leadId, student.id(), student.studentCode(), request.courseId());

        return new AdmissionResultResponse(
                leadId, student.id(), student.studentCode(), student.userId(), student.email(),
                request.courseId(), request.batchId(), true,
                "Admission complete. The student has been emailed their sign-in details, "
                        + "and a fee plan is being raised.");
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    /**
     * Splits a single enquiry name into first and last.
     *
     * <p>A lead form asks for one name because that is what a person gives on
     * the phone; the account needs two. Everything before the last space becomes
     * the first name, so "Priya Ramesh Sharma" splits sensibly. A single word
     * gets a placeholder surname, which staff correct on the profile — better
     * than refusing the admission over a formatting detail.
     */
    private NameParts splitName(String fullName) {
        String cleaned = fullName.trim().replaceAll("\\s+", " ");
        int lastSpace = cleaned.lastIndexOf(' ');
        if (lastSpace < 0) {
            return new NameParts(cleaned, ".");
        }
        return new NameParts(cleaned.substring(0, lastSpace), cleaned.substring(lastSpace + 1));
    }

    private String buildRemark(Lead lead) {
        StringBuilder remark = new StringBuilder("Admitted from lead #").append(lead.getId())
                .append(" (source: ").append(lead.getSource().name()).append(")");
        if (lead.getNotes() != null && !lead.getNotes().isBlank()) {
            remark.append("\nEnquiry notes: ").append(lead.getNotes());
        }
        return remark.toString();
    }

    private LeadStatus parseStatus(String value) {
        try {
            return LeadStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new BusinessRuleException("Unknown lead status: " + value);
        }
    }

    private LeadSource parseSource(String value) {
        try {
            return LeadSource.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new BusinessRuleException("Unknown lead source: " + value);
        }
    }

    private FollowupOutcome parseOutcome(String value) {
        try {
            return FollowupOutcome.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new BusinessRuleException("Unknown follow-up outcome: " + value);
        }
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record NameParts(String first, String last) {
    }
}
