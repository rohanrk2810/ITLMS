package com.itilms.admission.service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Pageable;

import com.itilms.admission.dto.request.CreateStudentRequest;
import com.itilms.admission.dto.request.UpdateStudentRequest;
import com.itilms.admission.dto.response.StudentResponse;
import com.itilms.admission.dto.response.StudentSummaryResponse;
import com.itilms.admission.entity.LeadSource;
import com.itilms.common.dto.PageResponse;

/** Student profiles: the academic identity eight other services refer to. */
public interface StudentService {

    PageResponse<StudentSummaryResponse> search(String status, String query, Pageable pageable);

    StudentResponse get(Long id);

    /** Resolves the caller's own profile from their user id. */
    StudentResponse getByUserId(Long userId);

    /**
     * Admits a student, creating the login account as a side effect and
     * announcing the admission so finance, batch and notification can follow up.
     *
     * @param source how they arrived, recorded for channel reporting
     * @param terms  the course, batch and fee agreed, or {@link AdmissionTerms#none()}
     *               when the profile is being recorded ahead of any of that
     */
    StudentResponse create(CreateStudentRequest request, LeadSource source, AdmissionTerms terms);

    StudentResponse update(Long id, UpdateStudentRequest request);

    StudentResponse updateStatus(Long id, String status, String reason);

    /** Bulk resolution for batch rosters, fee reports and attendance sheets. */
    List<StudentSummaryResponse> findByIds(Collection<Long> ids);

    /** Headline counts for the admin dashboard. */
    Map<String, Long> counts();

    /**
     * What was agreed commercially at admission.
     *
     * <p>Kept out of {@code CreateStudentRequest} because the two are settled at
     * different moments by different people: a coordinator records the person,
     * a counselor negotiates the fee. Bundling them would force every direct
     * profile creation to invent fee figures it has no basis for.
     */
    record AdmissionTerms(
            Long courseId,
            Long batchId,
            BigDecimal totalFee,
            BigDecimal discount,
            Integer installments
    ) {

        public static AdmissionTerms none() {
            return new AdmissionTerms(null, null, null, null, null);
        }

        public boolean isEmpty() {
            return courseId == null && totalFee == null;
        }
    }
}
