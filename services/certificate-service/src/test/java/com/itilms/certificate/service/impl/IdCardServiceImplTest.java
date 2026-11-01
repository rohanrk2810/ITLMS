package com.itilms.certificate.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.itilms.certificate.client.AdmissionClient;
import com.itilms.certificate.client.BatchClient;
import com.itilms.certificate.config.CertificateProperties;
import com.itilms.certificate.dto.request.IssueIdCardRequest;
import com.itilms.certificate.entity.IdCard;
import com.itilms.certificate.entity.IdCardPersonType;
import com.itilms.certificate.entity.IdCardStatus;
import com.itilms.certificate.repository.IdCardPhotoRepository;
import com.itilms.certificate.repository.IdCardRepository;
import com.itilms.certificate.service.IdCardPdfRenderer;
import com.itilms.certificate.service.InstituteBranding;
import com.itilms.common.event.EventPublisher;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.ForbiddenOperationException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.common.security.AppPrincipal;

@ExtendWith(MockitoExtension.class)
class IdCardServiceImplTest {

    @Mock IdCardRepository cards;
    @Mock IdCardPhotoRepository photos;
    @Mock AdmissionClient admissionClient;
    @Mock BatchClient batchClient;
    @Mock EventPublisher events;

    CertificateProperties props = new CertificateProperties();
    IdCardServiceImpl service;

    @BeforeEach
    void setUp() {
        InstituteBranding branding = new InstituteBranding(null, props);
        service = new IdCardServiceImpl(cards, photos, admissionClient, batchClient,
                new IdCardPdfRenderer(branding), branding, props, events);
        lenient().when(cards.save(any(IdCard.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(cards.nextSequence()).thenReturn(42L);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void signInAs(String role, Long userId) {
        AppPrincipal p = new AppPrincipal(userId, "u@x", "User", role, null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(p, null, p.authorities()));
    }

    private static IssueIdCardRequest student(Long id, Long batchId) {
        return new IssueIdCardRequest(IdCardPersonType.STUDENT, id, null, null, batchId, null);
    }

    private LocalDate today() {
        return LocalDate.now(props.getZone());
    }

    // ------------------------------------------------------------------ issuing

    @Test
    void studentCardCopiesTheirDetailsAndEndsWithTheBatch() {
        signInAs("ADMIN", 1L);
        when(admissionClient.lookup(List.of(30L)))
                .thenReturn(List.of(new AdmissionClient.Student(30L, 7L, "STU-30", "Asha Patil", "ACTIVE")));
        LocalDate end = today().plusMonths(5);
        when(batchClient.batch(9L)).thenReturn(new BatchClient.BatchInfo(9L, "B-9", "Morning Batch", end));

        var response = service.issue(student(30L, 9L));

        assertThat(response.cardNo()).isEqualTo("STU-%d-0042".formatted(today().getYear()));
        assertThat(response.fullName()).isEqualTo("Asha Patil");
        assertThat(response.personCode()).isEqualTo("STU-30");
        assertThat(response.designation()).isEqualTo("Student");
        assertThat(response.batchName()).isEqualTo("Morning Batch");
        assertThat(response.validUntil()).isEqualTo(end);
        assertThat(response.status()).isEqualTo("ISSUED");
        assertThat(response.verificationUrl()).contains(response.cardNo()).contains(response.verificationCode());
    }

    @Test
    void studentWithoutABatchEndDateGetsOneYear() {
        signInAs("COORDINATOR", 1L);
        when(admissionClient.lookup(List.of(30L)))
                .thenReturn(List.of(new AdmissionClient.Student(30L, 7L, "STU-30", "Asha Patil", "ACTIVE")));

        var response = service.issue(student(30L, null));

        assertThat(response.validUntil()).isEqualTo(today().plusYears(1));
    }

    @Test
    void trainerCardUsesTheEmployeeCode() {
        signInAs("ADMIN", 1L);
        when(admissionClient.lookupTrainers(List.of(3L)))
                .thenReturn(List.of(new AdmissionClient.Trainer(3L, 11L, "EMP-3", "Ravi Kulkarni", "ACTIVE")));

        var response = service.issue(new IssueIdCardRequest(IdCardPersonType.TRAINER, 3L, null, null, null, null));

        assertThat(response.cardNo()).startsWith("TRN-");
        assertThat(response.personCode()).isEqualTo("EMP-3");
        assertThat(response.designation()).isEqualTo("Trainer");
    }

    @Test
    void staffCardNeedsAName() {
        signInAs("ADMIN", 1L);

        assertThatThrownBy(() -> service.issue(
                new IssueIdCardRequest(IdCardPersonType.STAFF, null, "  ", "Accountant", null, null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void staffCardUsesTheTitleOrFallsBackToStaff() {
        signInAs("ADMIN", 1L);

        var titled = service.issue(new IssueIdCardRequest(IdCardPersonType.STAFF, null, "Meera", "Accountant", null, null));
        var plain = service.issue(new IssueIdCardRequest(IdCardPersonType.STAFF, null, "Meera", null, null, null));

        assertThat(titled.designation()).isEqualTo("Accountant");
        assertThat(plain.designation()).isEqualTo("Staff");
        assertThat(titled.cardNo()).startsWith("STF-");
    }

    @Test
    void aStudentWhoDroppedOutGetsNoCard() {
        signInAs("ADMIN", 1L);
        when(admissionClient.lookup(List.of(30L)))
                .thenReturn(List.of(new AdmissionClient.Student(30L, 7L, "STU-30", "Asha Patil", "DROPPED")));

        assertThatThrownBy(() -> service.issue(student(30L, null))).isInstanceOf(BusinessRuleException.class);
        verify(cards, never()).save(any());
    }

    @Test
    void anUnreachableAdmissionServiceIsRefusedNotGuessed() {
        signInAs("ADMIN", 1L);
        when(admissionClient.lookup(List.of(30L))).thenReturn(List.of());

        assertThatThrownBy(() -> service.issue(student(30L, null))).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void validUntilInThePastIsRefused() {
        signInAs("ADMIN", 1L);

        assertThatThrownBy(() -> service.issue(new IssueIdCardRequest(IdCardPersonType.STAFF, null, "Meera", null,
                null, today().minusDays(1)))).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void aNewCardRevokesTheOneStillInForce() {
        signInAs("ADMIN", 1L);
        when(admissionClient.lookup(List.of(30L)))
                .thenReturn(List.of(new AdmissionClient.Student(30L, 7L, "STU-30", "Asha Patil", "ACTIVE")));
        IdCard old = IdCard.builder().id(5L).personType(IdCardPersonType.STUDENT).personId(30L).cardNo("STU-2026-0001")
                .fullName("Asha Patil").validUntil(today().plusMonths(2)).build();
        when(cards.findByPersonTypeAndPersonIdAndStatus(IdCardPersonType.STUDENT, 30L, IdCardStatus.ISSUED))
                .thenReturn(List.of(old));

        service.issue(student(30L, null));

        assertThat(old.getStatus()).isEqualTo(IdCardStatus.REVOKED);
        assertThat(old.getRevokedReason()).isEqualTo("Replaced by a new card");
    }

    // ------------------------------------------------------------------ photo

    private static IdCard card(Long id, Long userId) {
        return IdCard.builder().id(id).cardNo("STU-2026-0007").verificationCode("K7QMX-2HWRP").userId(userId)
                .personType(IdCardPersonType.STUDENT).personId(30L).fullName("Asha Patil").designation("Student")
                .issuedOn(LocalDate.now().minusDays(1)).validUntil(LocalDate.now().plusYears(1)).build();
    }

    @Test
    void photoTypeComesFromTheBytesNotTheClaim() {
        signInAs("ADMIN", 1L);
        when(cards.findById(5L)).thenReturn(Optional.of(card(5L, 7L)));

        byte[] html = "<html>not a picture</html>".getBytes();
        assertThatThrownBy(() -> service.setPhoto(5L, html, "image/png")).isInstanceOf(BusinessRuleException.class);

        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};
        var response = service.setPhoto(5L, jpeg, "text/plain");
        assertThat(response.hasPhoto()).isTrue();
    }

    @Test
    void anOversizedPhotoIsRefused() {
        signInAs("ADMIN", 1L);
        when(cards.findById(5L)).thenReturn(Optional.of(card(5L, 7L)));
        byte[] big = new byte[IdCardServiceImpl.MAX_PHOTO_BYTES + 1];
        big[0] = (byte) 0xFF;
        big[1] = (byte) 0xD8;
        big[2] = (byte) 0xFF;

        assertThatThrownBy(() -> service.setPhoto(5L, big, "image/jpeg")).isInstanceOf(BusinessRuleException.class);
    }

    // ------------------------------------------------------------------ who may see a card

    @Test
    void aStudentSeesOnlyTheirOwnCard() {
        when(cards.findById(5L)).thenReturn(Optional.of(card(5L, 7L)));

        signInAs("STUDENT", 8L);
        assertThatThrownBy(() -> service.get(5L)).isInstanceOf(ForbiddenOperationException.class);

        signInAs("STUDENT", 7L);
        assertThat(service.get(5L).cardNo()).isEqualTo("STU-2026-0007");
    }

    @Test
    void staffSeeAnyCardButATrainerDoesNot() {
        when(cards.findById(5L)).thenReturn(Optional.of(card(5L, 7L)));

        signInAs("COORDINATOR", 2L);
        assertThat(service.get(5L).id()).isEqualTo(5L);

        signInAs("TRAINER", 3L);
        assertThatThrownBy(() -> service.get(5L)).isInstanceOf(ForbiddenOperationException.class);
    }

    // ------------------------------------------------------------------ printing, revoking, verifying

    @Test
    void aRevokedOrExpiredCardCannotBePrinted() {
        signInAs("ADMIN", 1L);
        IdCard revoked = card(5L, 7L);
        revoked.revoke("lost", 1L, java.time.Instant.now());
        when(cards.findById(5L)).thenReturn(Optional.of(revoked));
        assertThatThrownBy(() -> service.pdf(5L)).isInstanceOf(BusinessRuleException.class);

        IdCard expired = card(6L, 7L);
        expired.setValidUntil(today().minusDays(1));
        when(cards.findById(6L)).thenReturn(Optional.of(expired));
        assertThatThrownBy(() -> service.pdf(6L)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void aValidCardPrintsAsAPdf() {
        signInAs("ADMIN", 1L);
        when(cards.findById(5L)).thenReturn(Optional.of(card(5L, 7L)));

        byte[] pdf = service.pdf(5L);

        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void aSheetSkipsCardsThatCannotBePrintedAndRefusesWhenNoneCan() {
        signInAs("ADMIN", 1L);
        IdCard good = card(5L, 7L);
        IdCard revoked = card(6L, 8L);
        revoked.revoke("lost", 1L, java.time.Instant.now());
        when(cards.findAllById(List.of(5L, 6L))).thenReturn(List.of(good, revoked));
        assertThat(new String(service.sheet(List.of(5L, 6L)), 0, 4)).isEqualTo("%PDF");

        when(cards.findAllById(List.of(6L))).thenReturn(List.of(revoked));
        assertThatThrownBy(() -> service.sheet(List.of(6L))).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.sheet(List.of())).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void revokingTwiceIsRefusedAndTheOwnerIsTold() {
        signInAs("ADMIN", 1L);
        IdCard c = card(5L, 7L);
        when(cards.findById(5L)).thenReturn(Optional.of(c));

        service.revoke(5L, "  lost  ");

        assertThat(c.getRevokedReason()).isEqualTo("lost");
        verify(events).notifyUsers(any(), any(), any(), any(), any());
        assertThatThrownBy(() -> service.revoke(5L, "again")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void publicCheckNeedsTheRightCodeAndShowsLittle() {
        IdCard c = card(5L, 7L);
        when(cards.findByCardNo("STU-2026-0007")).thenReturn(Optional.of(c));

        var ok = service.verify("stu-2026-0007", "k7qmx 2hwrp");
        assertThat(ok.status()).isEqualTo("VALID");
        assertThat(ok.fullName()).isEqualTo("Asha Patil");

        assertThatThrownBy(() -> service.verify("STU-2026-0007", "WRONG-CODE1"))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.verify("STU-2026-0007", " "))
                .isInstanceOf(BusinessRuleException.class);

        c.revoke("lost", 1L, java.time.Instant.now());
        assertThat(service.verify("STU-2026-0007", "K7QMX-2HWRP").status()).isEqualTo("REVOKED");
    }

    @Test
    void anExpiredCardChecksAsExpired() {
        IdCard c = card(5L, 7L);
        c.setValidUntil(today().minusDays(3));
        when(cards.findByCardNo("STU-2026-0007")).thenReturn(Optional.of(c));

        assertThat(service.verify("STU-2026-0007", "K7QMX-2HWRP").status()).isEqualTo("EXPIRED");
    }

    @Test
    void sniffRecognisesOnlyJpegAndPng() {
        assertThat(IdCardServiceImpl.sniff(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0})).isEqualTo("image/png");
        assertThat(IdCardServiceImpl.sniff(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9})).isNull();
    }
}
