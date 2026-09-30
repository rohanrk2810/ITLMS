package com.itilms.certificate.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itilms.certificate.config.CertificateProperties;
import com.itilms.certificate.entity.Certificate;
import com.itilms.certificate.entity.CertificateStatus;
import com.itilms.certificate.repository.CertificateRepository;
import com.itilms.certificate.service.impl.CertificateServiceImpl;
import com.itilms.common.event.EventPublisher;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.ResourceNotFoundException;

class VerificationAndPdfTest {

    private static Certificate certificate(String code) {
        return Certificate.builder().id(1L).certificateNo("ITILMS-2026-0000007").verificationCode(code)
                .studentId(3L).studentName("Asha Patil").courseId(1L).courseTitle("Java Full Stack")
                .issueDate(LocalDate.of(2026, 9, 18)).status(CertificateStatus.ISSUED).build();
    }

    @Test
    @DisplayName("Codes are two groups of five, without characters people misread")
    void codeShape() {
        for (int i = 0; i < 200; i++) {
            assertThat(VerificationCodes.generate()).matches("[A-HJKMNP-Z2-9]{5}-[A-HJKMNP-Z2-9]{5}");
        }
    }

    @Test
    @DisplayName("A code typed by hand matches regardless of case, spaces or the dash")
    void forgivingMatch() {
        assertThat(VerificationCodes.matches("K7QMX-2HWRP", "k7qmx 2hwrp")).isTrue();
        assertThat(VerificationCodes.matches("K7QMX-2HWRP", "K7QMX2HWRP")).isTrue();
        assertThat(VerificationCodes.matches("K7QMX-2HWRP", "K7QMX-2HWRQ")).isFalse();
        assertThat(VerificationCodes.matches("K7QMX-2HWRP", null)).isFalse();
    }

    @Test
    @DisplayName("A wrong code and an unknown number get the same answer")
    void verificationDoesNotRevealExistence() {
        CertificateRepository repository = mock(CertificateRepository.class);
        when(repository.findByCertificateNo("ITILMS-2026-0000007")).thenReturn(Optional.of(certificate("K7QMX-2HWRP")));
        when(repository.findByCertificateNo("ITILMS-2026-0000008")).thenReturn(Optional.empty());
        CertificateServiceImpl service = new CertificateServiceImpl(repository, null, null, null, null, null,
                null, new InstituteBranding(null, new CertificateProperties()), null,
                new CertificateProperties(), mock(EventPublisher.class), new ObjectMapper());

        assertThat(service.verify("ITILMS-2026-0000007", "k7qmx-2hwrp").status()).isEqualTo("VALID");

        String wrongCode = messageOf(() -> service.verify("ITILMS-2026-0000007", "AAAAA-BBBBB"));
        String unknown = messageOf(() -> service.verify("ITILMS-2026-0000008", "AAAAA-BBBBB"));
        assertThat(wrongCode).isEqualTo(unknown);

        assertThatThrownBy(() -> service.verify("ITILMS-2026-0000007", " "))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("The certificate renders as a PDF")
    void rendersPdf() {
        byte[] pdf = new CertificatePdfRenderer(new CertificateProperties(),
                new InstituteBranding(null, new CertificateProperties()))
                .render(certificate("K7QMX-2HWRP"), "http://localhost:5173/verify/ITILMS-2026-0000007?code=K7QMX-2HWRP");

        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(pdf.length).isGreaterThan(1_000);
    }

    private static String messageOf(Runnable call) {
        try {
            call.run();
            throw new AssertionError("expected a refusal");
        } catch (ResourceNotFoundException ex) {
            return ex.getMessage();
        }
    }
}
