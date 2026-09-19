package com.itilms.admission.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.itilms.admission.dto.request.AddFollowupRequest;
import com.itilms.admission.dto.request.ConvertLeadRequest;
import com.itilms.admission.dto.request.CreateLeadRequest;
import com.itilms.admission.dto.request.CreateStudentRequest;
import com.itilms.admission.dto.request.PublicEnquiryRequest;
import com.itilms.admission.dto.request.UpdateLeadRequest;
import com.itilms.admission.dto.response.AdmissionResultResponse;
import com.itilms.admission.dto.response.LeadResponse;
import com.itilms.admission.dto.response.StudentResponse;
import com.itilms.admission.entity.Lead;
import com.itilms.admission.entity.LeadFollowup;
import com.itilms.admission.entity.LeadSource;
import com.itilms.admission.entity.LeadStatus;
import com.itilms.admission.entity.Student;
import com.itilms.admission.repository.LeadFollowupRepository;
import com.itilms.admission.repository.LeadRepository;
import com.itilms.admission.service.StudentService;
import com.itilms.admission.service.StudentService.AdmissionTerms;
import com.itilms.common.event.EventPublisher;
import com.itilms.common.event.KafkaTopics;
import com.itilms.common.event.NotificationRequestedEvent;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.common.security.AppPrincipal;

/** The enquiry-to-admission pipeline: what a counselor may do to a lead, and what admitting one sets in motion. */
@ExtendWith(MockitoExtension.class)
class LeadServiceImplTest {

    @Mock LeadRepository leadRepository;
    @Mock LeadFollowupRepository followupRepository;
    @Mock StudentService studentService;
    @Mock EventPublisher events;

    LeadServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LeadServiceImpl(leadRepository, followupRepository, studentService, events);
        lenient().when(leadRepository.save(any(Lead.class))).thenAnswer(i -> {
            Lead lead = i.getArgument(0);
            if (lead.getId() == null) {
                lead.setId(50L);
            }
            return lead;
        });
        lenient().when(followupRepository.save(any(LeadFollowup.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(followupRepository.countByLeadId(any())).thenReturn(0L);
        AppPrincipal counselor = new AppPrincipal(7L, "c@x", "Counselor", "COORDINATOR", null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(counselor, null, counselor.authorities()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private Lead lead(LeadStatus status, String email) {
        return Lead.builder().id(50L).name("Priya Ramesh Sharma").phone("9800000001").email(email)
                .source(LeadSource.WALK_IN).status(status).notes("Wants evening batch").build();
    }

    private UpdateLeadRequest update(String status, String lostReason) {
        return new UpdateLeadRequest(" Priya Sharma ", "9800000001", "p@test.local", "WALK_IN", status,
                null, 7L, Instant.now().plus(2, ChronoUnit.DAYS), null, lostReason);
    }

    // ------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Capturing leads")
    class Capture {

        @Test
        void aWebsiteEnquiryLandsUnassignedAndNewAndTellsTheDesk() {
            LeadResponse response = service.captureEnquiry(
                    new PublicEnquiryRequest("  Asha  ", " 9800000002 ", "  ", 3L, " Call me "));

            ArgumentCaptor<Lead> saved = ArgumentCaptor.forClass(Lead.class);
            verify(leadRepository).save(saved.capture());
            Lead lead = saved.getValue();
            assertThat(lead.getName()).isEqualTo("Asha");
            assertThat(lead.getPhone()).isEqualTo("9800000002");
            assertThat(lead.getEmail()).as("a blank email is stored as absent").isNull();
            assertThat(lead.getSource()).isEqualTo(LeadSource.WEBSITE);
            assertThat(lead.getStatus()).isEqualTo(LeadStatus.NEW);
            assertThat(lead.getCounselorUserId()).as("nobody owns a public enquiry until the desk picks it up").isNull();
            assertThat(response).isNotNull();
            verify(events).publishAfterCommit(eq(KafkaTopics.NOTIFICATION_REQUESTED), any(NotificationRequestedEvent.class));
        }

        @Test
        void aLeadRecordedByStaffIsOwnedByThemUnlessTheyNameSomeoneElse() {
            service.create(new CreateLeadRequest("Ravi", "9800000003", null, "referral", null, null, null, null));
            service.create(new CreateLeadRequest("Meera", "9800000004", null, "PHONE", null, 9L, null, null));

            ArgumentCaptor<Lead> saved = ArgumentCaptor.forClass(Lead.class);
            verify(leadRepository, times(2)).save(saved.capture());
            assertThat(saved.getAllValues().get(0).getCounselorUserId()).isEqualTo(7L);
            assertThat(saved.getAllValues().get(0).getSource()).isEqualTo(LeadSource.REFERRAL);
            assertThat(saved.getAllValues().get(1).getCounselorUserId()).isEqualTo(9L);
        }

        @Test
        void anUnknownSourceIsRefused() {
            assertThatThrownBy(() -> service.create(new CreateLeadRequest("Ravi", "9800000003", null, "BILLBOARD", null, null, null, null)))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("BILLBOARD");
            verify(leadRepository, never()).save(any());
        }
    }

    // ------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Updating a lead")
    class Update {

        @Test
        void anAdmittedLeadIsReadOnly() {
            when(leadRepository.findById(50L)).thenReturn(Optional.of(lead(LeadStatus.CONVERTED, "p@test.local")));

            assertThatThrownBy(() -> service.update(50L, update("INTERESTED", null)))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("already been admitted");
        }

        @Test
        void conversionCannotBeSetByEditingTheStatus() {
            when(leadRepository.findById(50L)).thenReturn(Optional.of(lead(LeadStatus.INTERESTED, "p@test.local")));

            assertThatThrownBy(() -> service.update(50L, update("CONVERTED", null)))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("/convert");
            verify(leadRepository, never()).save(any());
        }

        @Test
        void losingALeadNeedsAReason() {
            when(leadRepository.findById(50L)).thenReturn(Optional.of(lead(LeadStatus.INTERESTED, "p@test.local")));

            assertThatThrownBy(() -> service.update(50L, update("LOST", null))).isInstanceOf(BusinessRuleException.class);
            assertThatThrownBy(() -> service.update(50L, update("NOT_INTERESTED", "   "))).isInstanceOf(BusinessRuleException.class);
            verify(leadRepository, never()).save(any());
        }

        @Test
        void aLostLeadWithAReasonLeavesTheFollowUpQueueAndIsAudited() {
            Lead existing = lead(LeadStatus.INTERESTED, "p@test.local");
            when(leadRepository.findById(50L)).thenReturn(Optional.of(existing));

            service.update(50L, update("lost", "Joined a competitor"));

            assertThat(existing.getStatus()).isEqualTo(LeadStatus.LOST);
            assertThat(existing.getLostReason()).isEqualTo("Joined a competitor");
            assertThat(existing.getNextFollowUpAt()).as("a closed lead is nobody's task").isNull();
            assertThat(existing.getName()).isEqualTo("Priya Sharma");
            verify(events).audit(eq("admission-service"), eq("LEAD_STATUS_CHANGED"), eq("Lead"), eq(50L), any(), any());
        }

        @Test
        void anEditThatKeepsTheStatusIsNotAuditedAsAStatusChange() {
            Lead existing = lead(LeadStatus.INTERESTED, "p@test.local");
            when(leadRepository.findById(50L)).thenReturn(Optional.of(existing));

            service.update(50L, update("INTERESTED", null));

            verify(events, never()).audit(anyString(), anyString(), anyString(), any(), any(), any());
            assertThat(existing.getNextFollowUpAt()).isNotNull();
        }

        @Test
        void anUnknownLeadIsNotFound() {
            when(leadRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(404L, update("NEW", null))).isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Follow-ups")
    class Followups {

        @Test
        void theFirstContactMovesANewLeadToContactedAndSetsTheNextFollowUp() {
            Lead existing = lead(LeadStatus.NEW, "p@test.local");
            when(leadRepository.findById(50L)).thenReturn(Optional.of(existing));
            Instant next = Instant.now().plus(3, ChronoUnit.DAYS);

            service.addFollowup(50L, new AddFollowupRequest("called", " Will visit Saturday ", next, null));

            assertThat(existing.getStatus()).isEqualTo(LeadStatus.CONTACTED);
            assertThat(existing.getNextFollowUpAt()).isEqualTo(next);
            ArgumentCaptor<LeadFollowup> saved = ArgumentCaptor.forClass(LeadFollowup.class);
            verify(followupRepository).save(saved.capture());
            assertThat(saved.getValue().getRemark()).isEqualTo("Will visit Saturday");
            assertThat(saved.getValue().getCreatedBy()).isEqualTo(7L);
        }

        @Test
        void aFollowUpOnALaterStageDoesNotDragTheStatusBack() {
            Lead existing = lead(LeadStatus.INTERESTED, "p@test.local");
            when(leadRepository.findById(50L)).thenReturn(Optional.of(existing));

            service.addFollowup(50L, new AddFollowupRequest("EMAILED", null, null, null));

            assertThat(existing.getStatus()).isEqualTo(LeadStatus.INTERESTED);
        }

        @Test
        void closingThroughAFollowUpClearsTheNextFollowUp() {
            Lead existing = lead(LeadStatus.FOLLOW_UP, "p@test.local");
            when(leadRepository.findById(50L)).thenReturn(Optional.of(existing));

            service.addFollowup(50L, new AddFollowupRequest("CALLED", "Not interested", Instant.now().plusSeconds(3600), "NOT_INTERESTED"));

            assertThat(existing.getStatus()).isEqualTo(LeadStatus.NOT_INTERESTED);
            assertThat(existing.getNextFollowUpAt()).isNull();
        }

        @Test
        void aFollowUpCannotAdmitTheLead() {
            when(leadRepository.findById(50L)).thenReturn(Optional.of(lead(LeadStatus.INTERESTED, "p@test.local")));

            assertThatThrownBy(() -> service.addFollowup(50L, new AddFollowupRequest("CALLED", null, null, "CONVERTED")))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("/convert");
        }

        @Test
        void anAdmittedLeadTakesNoMoreFollowUpsAndAnUnknownOutcomeIsRefused() {
            when(leadRepository.findById(50L)).thenReturn(Optional.of(lead(LeadStatus.CONVERTED, "p@test.local")));
            assertThatThrownBy(() -> service.addFollowup(50L, new AddFollowupRequest("CALLED", null, null, null)))
                    .isInstanceOf(BusinessRuleException.class);

            when(leadRepository.findById(51L)).thenReturn(Optional.of(lead(LeadStatus.NEW, "p@test.local")));
            assertThatThrownBy(() -> service.addFollowup(51L, new AddFollowupRequest("TELEPATHY", null, null, null)))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("TELEPATHY");
        }
    }

    // ------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Admitting a lead")
    class Convert {

        private final ConvertLeadRequest terms = new ConvertLeadRequest(3L, 4L, new BigDecimal("45000"), new BigDecimal("5000"), 3, null);

        private StudentResponse admitted() {
            Student student = Student.builder().id(88L).userId(99L).studentCode("STU-2026-000001")
                    .fullName("Priya Ramesh Sharma").email("p@test.local").phone("9800000001").build();
            return StudentResponse.from(student);
        }

        @Test
        void admitsTheStudentWithTheAgreedTermsAndMarksTheLeadConverted() {
            Lead existing = lead(LeadStatus.INTERESTED, "p@test.local");
            when(leadRepository.findById(50L)).thenReturn(Optional.of(existing));
            when(studentService.create(any(), any(), any())).thenReturn(admitted());

            AdmissionResultResponse result = service.convert(50L, terms);

            ArgumentCaptor<CreateStudentRequest> student = ArgumentCaptor.forClass(CreateStudentRequest.class);
            ArgumentCaptor<AdmissionTerms> admission = ArgumentCaptor.forClass(AdmissionTerms.class);
            verify(studentService).create(student.capture(), eq(LeadSource.WALK_IN), admission.capture());
            assertThat(student.getValue().firstName()).as("everything before the last word").isEqualTo("Priya Ramesh");
            assertThat(student.getValue().lastName()).isEqualTo("Sharma");
            assertThat(student.getValue().email()).isEqualTo("p@test.local");
            assertThat(student.getValue().remarks()).contains("lead #50").contains("Wants evening batch");
            assertThat(admission.getValue().courseId()).isEqualTo(3L);
            assertThat(admission.getValue().batchId()).isEqualTo(4L);
            assertThat(admission.getValue().totalFee()).isEqualByComparingTo("45000");
            assertThat(admission.getValue().discount()).isEqualByComparingTo("5000");
            assertThat(admission.getValue().installments()).isEqualTo(3);

            assertThat(existing.isConverted()).isTrue();
            assertThat(existing.getConvertedStudentId()).isEqualTo(88L);
            assertThat(result.studentId()).isEqualTo(88L);
            verify(events).audit(eq("admission-service"), eq("LEAD_CONVERTED"), eq("Lead"), eq(50L), any(), any());
        }

        @Test
        void aSingleWordNameGetsAPlaceholderSurname() {
            Lead existing = lead(LeadStatus.INTERESTED, "p@test.local");
            existing.setName("Madonna");
            when(leadRepository.findById(50L)).thenReturn(Optional.of(existing));
            when(studentService.create(any(), any(), any())).thenReturn(admitted());

            service.convert(50L, terms);

            ArgumentCaptor<CreateStudentRequest> student = ArgumentCaptor.forClass(CreateStudentRequest.class);
            verify(studentService).create(student.capture(), any(), any());
            assertThat(student.getValue().firstName()).isEqualTo("Madonna");
            assertThat(student.getValue().lastName()).isEqualTo(".");
        }

        @Test
        void missingDiscountAndInstalmentsDefaultToNoneAndOne() {
            when(leadRepository.findById(50L)).thenReturn(Optional.of(lead(LeadStatus.INTERESTED, "p@test.local")));
            when(studentService.create(any(), any(), any())).thenReturn(admitted());

            service.convert(50L, new ConvertLeadRequest(3L, null, new BigDecimal("45000"), null, null, null));

            ArgumentCaptor<AdmissionTerms> admission = ArgumentCaptor.forClass(AdmissionTerms.class);
            verify(studentService).create(any(), any(), admission.capture());
            assertThat(admission.getValue().discount()).isEqualByComparingTo("0");
            assertThat(admission.getValue().installments()).isEqualTo(1);
        }

        @Test
        void aLeadWithoutAnEmailCannotBeAdmittedBecauseTheirPasswordWouldGoNowhere() {
            when(leadRepository.findById(50L)).thenReturn(Optional.of(lead(LeadStatus.INTERESTED, null)));

            assertThatThrownBy(() -> service.convert(50L, terms))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("email");
            verify(studentService, never()).create(any(), any(), any());
        }

        @Test
        void aDiscountLargerThanTheFeeIsRefused() {
            when(leadRepository.findById(50L)).thenReturn(Optional.of(lead(LeadStatus.INTERESTED, "p@test.local")));

            assertThatThrownBy(() -> service.convert(50L, new ConvertLeadRequest(3L, 4L, new BigDecimal("1000"), new BigDecimal("1001"), 1, null)))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("discount");
            verify(studentService, never()).create(any(), any(), any());
        }

        @Test
        void aLeadIsAdmittedOnlyOnce() {
            Lead existing = lead(LeadStatus.CONVERTED, "p@test.local");
            existing.setConvertedStudentId(88L);
            when(leadRepository.findById(50L)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> service.convert(50L, terms))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("88");
            verify(studentService, never()).create(any(), any(), any());
        }
    }

    // ------------------------------------------------------------------------------------

    @Test
    void theFunnelListsEveryStageEvenWhenEmptyAndAddsATotal() {
        List<Object[]> grouped = new ArrayList<>();
        grouped.add(new Object[]{LeadStatus.NEW, 4L});
        grouped.add(new Object[]{LeadStatus.CONVERTED, 1L});
        when(leadRepository.countGroupedByStatus()).thenReturn(grouped);

        var funnel = service.funnel();

        assertThat(funnel).containsEntry("NEW", 4L).containsEntry("CONVERTED", 1L).containsEntry("LOST", 0L).containsEntry("TOTAL", 5L);
        assertThat(funnel.keySet()).contains("CONTACTED", "FOLLOW_UP", "INTERESTED", "NOT_INTERESTED");
    }
}
