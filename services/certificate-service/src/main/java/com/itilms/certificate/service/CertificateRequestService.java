package com.itilms.certificate.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Pageable;

import com.itilms.certificate.dto.response.CertificateRequestDetailResponse;
import com.itilms.certificate.dto.response.CertificateRequestResponse;
import com.itilms.certificate.dto.response.CertificateResponse;
import com.itilms.certificate.entity.CertificateRequestStatus;
import com.itilms.common.dto.PageResponse;

/** The request, approval and issue workflow around a course certificate. */
public interface CertificateRequestService {

    /** A student asks for a certificate. Refused unless eligible, and while another request is open. */
    CertificateRequestResponse request(Long courseId);

    List<CertificateRequestResponse> mine();

    PageResponse<CertificateRequestResponse> search(List<CertificateRequestStatus> statuses, Long courseId,
                                                    Long batchId, String query, LocalDate from, LocalDate to,
                                                    Pageable pageable);

    CertificateRequestDetailResponse get(Long id);

    /** PENDING to APPROVED. Does not yet issue anything. */
    CertificateRequestResponse approve(Long id);

    /** PENDING or APPROVED to REJECTED, with a reason the student is shown. */
    CertificateRequestResponse reject(Long id, String reason);

    /** APPROVED to ISSUED: creates the certificate, or changes nothing if that fails. */
    CertificateResponse issue(Long id);
}
