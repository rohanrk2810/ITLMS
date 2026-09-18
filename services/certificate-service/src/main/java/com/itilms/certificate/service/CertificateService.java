package com.itilms.certificate.service;

import java.util.List;

import org.springframework.data.domain.Pageable;

import com.itilms.certificate.dto.response.CertificateResponse;
import com.itilms.certificate.dto.response.EligibilityResponse;
import com.itilms.certificate.dto.response.VerificationResponse;
import com.itilms.common.dto.PageResponse;

/** Certificates (Doc S6.13, S7.3). */
public interface CertificateService {

    /** The completion rule, checked now against the services that own each fact. */
    EligibilityResponse eligibility(Long studentId, Long courseId);

    /** Issues a certificate, re-checking every criterion first (Doc S14). */
    CertificateResponse issue(Long studentId, Long courseId);

    /** A student claiming their own certificate once they are eligible. */
    CertificateResponse claim(Long courseId);

    /** Doc S11: GET /api/certificates/me. */
    List<CertificateResponse> mine();

    CertificateResponse get(Long id);

    PageResponse<CertificateResponse> list(Long courseId, Pageable pageable);

    CertificateResponse revoke(Long id, String reason);

    /** The printable certificate as a PDF (Doc S15.1). */
    byte[] pdf(Long id);

    /**
     * Doc S11: GET /api/certificates/verify/{certificateNo}. Public.
     *
     * <p>Requires the verification code printed on the certificate. A wrong
     * code and a number that does not exist get the same answer, so the page
     * cannot be used to discover which numbers exist.
     */
    VerificationResponse verify(String certificateNo, String code);
}
