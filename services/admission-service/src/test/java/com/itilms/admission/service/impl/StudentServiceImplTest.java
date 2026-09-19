package com.itilms.admission.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.itilms.admission.client.IdentityClient;
import com.itilms.admission.client.IdentityClient.CreatedUser;
import com.itilms.admission.dto.request.CreateStudentRequest;
import com.itilms.admission.dto.request.UpdateStudentRequest;
import com.itilms.admission.dto.response.StudentResponse;
import com.itilms.admission.entity.LeadSource;
import com.itilms.admission.entity.Student;
import com.itilms.admission.entity.StudentStatus;
import com.itilms.admission.repository.StudentRepository;
import com.itilms.admission.service.StudentService.AdmissionTerms;
import com.itilms.common.event.EventPublisher;
import com.itilms.common.event.KafkaTopics;
import com.itilms.common.event.ProfileLinkedEvent;
import com.itilms.common.event.StudentAdmittedEvent;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.ResourceNotFoundException;

/** Admitting a student: the account, the profile, and the two events that start the rest of the workflow. */
@ExtendWith(MockitoExtension.class)
class StudentServiceImplTest {

    @Mock StudentRepository studentRepository;
    @Mock IdentityClient identityClient;
    @Mock EventPublisher events;

    StudentServiceImpl service;

    private static final AdmissionTerms TERMS =
            new AdmissionTerms(3L, 4L, new BigDecimal("45000"), new BigDecimal("5000"), 3);

    @BeforeEach
    void setUp() {
        service = new StudentServiceImpl(studentRepository, identityClient, events);
        lenient().when(identityClient.createUser(any())).thenReturn(
                new CreatedUser(99L, "priya@test.local", "9800000001", "Priya Sharma", "STUDENT", "ACTIVE"));
        lenient().when(studentRepository.existsByUserId(99L)).thenReturn(false);
        lenient().when(studentRepository.nextCodeSequence(anyInt())).thenReturn(7L);
        lenient().when(studentRepository.saveAndFlush(any(Student.class))).thenAnswer(i -> {
            Student s = i.getArgument(0);
            s.setId(88L);
            return s;
        });
    }

    private static CreateStudentRequest request() {
        return new CreateStudentRequest(" Priya ", " Sharma ", " Priya@Test.Local ", " 9800000001 ",
                null, "female", " B.Sc ", null, 2024, null, " Pune ", null, null, null, null, null, null, "  ");
    }

    // ------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Admission")
    class Create {

        @Test
        void createsALoginFirstThenTheProfileLinkedToItAndAnnouncesBoth() {
            StudentResponse response = service.create(request(), LeadSource.WALK_IN, TERMS);

            ArgumentCaptor<IdentityClient.CreateUserPayload> account = ArgumentCaptor.forClass(IdentityClient.CreateUserPayload.class);
            verify(identityClient).createUser(account.capture());
            assertThat(account.getValue().role()).isEqualTo("STUDENT");
            assertThat(account.getValue().email()).as("emails are lower-cased").isEqualTo("priya@test.local");
            assertThat(account.getValue().password()).as("the temporary password is generated, never chosen here").isNull();

            ArgumentCaptor<Student> profile = ArgumentCaptor.forClass(Student.class);
            verify(studentRepository).saveAndFlush(profile.capture());
            Student saved = profile.getValue();
            assertThat(saved.getUserId()).isEqualTo(99L);
            assertThat(saved.getFullName()).isEqualTo("Priya Sharma");
            assertThat(saved.getStatus()).isEqualTo(StudentStatus.ACTIVE);
            assertThat(saved.getAdmissionSource()).isEqualTo(LeadSource.WALK_IN);
            assertThat(saved.getStudentCode()).startsWith("STU-").endsWith("-000007");
            assertThat(saved.getCity()).isEqualTo("Pune");
            assertThat(saved.getRemarks()).as("a blank remark is stored as absent").isNull();
            assertThat(response.id()).isEqualTo(88L);

            ArgumentCaptor<ProfileLinkedEvent> linked = ArgumentCaptor.forClass(ProfileLinkedEvent.class);
            verify(events).publishAfterCommit(eq(KafkaTopics.PROFILE_LINKED), linked.capture());
            assertThat(linked.getValue().userId()).isEqualTo(99L);
            assertThat(linked.getValue().profileId()).isEqualTo(88L);
            assertThat(linked.getValue().profileType()).isEqualTo("STUDENT");

            ArgumentCaptor<StudentAdmittedEvent> admitted = ArgumentCaptor.forClass(StudentAdmittedEvent.class);
            verify(events).publishAfterCommit(eq(KafkaTopics.STUDENT_ADMITTED), admitted.capture());
            assertThat(admitted.getValue().courseId()).isEqualTo(3L);
            assertThat(admitted.getValue().batchId()).isEqualTo(4L);
            assertThat(admitted.getValue().totalFee()).isEqualByComparingTo("45000");
            assertThat(admitted.getValue().discount()).isEqualByComparingTo("5000");
            assertThat(admitted.getValue().installments()).isEqualTo(3);
            verify(events).audit(eq("admission-service"), eq("STUDENT_ADMITTED"), eq("Student"), eq(88L), any(), any());
        }

        @Test
        void anAccountThatAlreadyHasAProfileIsNotGivenASecondOne() {
            when(studentRepository.existsByUserId(99L)).thenReturn(true);

            assertThatThrownBy(() -> service.create(request(), LeadSource.WALK_IN, TERMS))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("already exists");

            verify(studentRepository, never()).saveAndFlush(any());
            verify(events, never()).publishAfterCommit(anyString(), any());
        }

        @Test
        void aCodeThatCollidesIsRetriedWithTheNextNumber() {
            when(studentRepository.saveAndFlush(any(Student.class)))
                    .thenThrow(new DataIntegrityViolationException("uk_students_code"))
                    .thenAnswer(i -> {
                        Student s = i.getArgument(0);
                        s.setId(88L);
                        return s;
                    });

            service.create(request(), LeadSource.WALK_IN, TERMS);

            ArgumentCaptor<Student> attempts = ArgumentCaptor.forClass(Student.class);
            verify(studentRepository, times(2)).saveAndFlush(attempts.capture());
            assertThat(attempts.getAllValues().get(0).getStudentCode()).endsWith("-000007");
            assertThat(attempts.getAllValues().get(1).getStudentCode()).endsWith("-000008");
        }

        @Test
        void givesUpAfterFiveCollisionsInsteadOfLoopingForever() {
            when(studentRepository.saveAndFlush(any(Student.class))).thenThrow(new DataIntegrityViolationException("uk_students_code"));

            assertThatThrownBy(() -> service.create(request(), LeadSource.WALK_IN, TERMS))
                    .isInstanceOf(DataIntegrityViolationException.class);

            verify(studentRepository, times(5)).saveAndFlush(any());
            verify(events, never()).publishAfterCommit(anyString(), any());
        }

        @Test
        void anUnknownGenderIsRefusedBeforeAnythingIsSaved() {
            CreateStudentRequest bad = new CreateStudentRequest("A", "B", "a@test.local", "9800000001",
                    null, "robot", null, null, null, null, null, null, null, null, null, null, null, null);

            assertThatThrownBy(() -> service.create(bad, LeadSource.WALK_IN, TERMS))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Gender");
            verify(studentRepository, never()).saveAndFlush(any());
        }
    }

    // ------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Status changes")
    class Status {

        private Student student(StudentStatus status) {
            return Student.builder().id(88L).userId(99L).studentCode("STU-2026-000007").fullName("Priya Sharma")
                    .email("p@test.local").phone("9800000001").status(status).build();
        }

        @Test
        void aChangeIsRecordedWithItsReason() {
            Student existing = student(StudentStatus.ACTIVE);
            when(studentRepository.findById(88L)).thenReturn(Optional.of(existing));

            service.updateStatus(88L, "dropped", "Relocated");

            assertThat(existing.getStatus()).isEqualTo(StudentStatus.DROPPED);
            verify(events).audit(eq("admission-service"), eq("STUDENT_STATUS_CHANGED"), eq("Student"), eq(88L), any(), any());
        }

        @Test
        void settingTheSameStatusAgainChangesAndAuditsNothing() {
            when(studentRepository.findById(88L)).thenReturn(Optional.of(student(StudentStatus.ACTIVE)));

            service.updateStatus(88L, "ACTIVE", null);

            verify(studentRepository, never()).save(any());
            verify(events, never()).audit(anyString(), anyString(), anyString(), any(), any(), any());
        }

        @Test
        void anUnknownStatusOrStudentIsRefused() {
            when(studentRepository.findById(88L)).thenReturn(Optional.of(student(StudentStatus.ACTIVE)));
            assertThatThrownBy(() -> service.updateStatus(88L, "GRADUATED", null)).isInstanceOf(BusinessRuleException.class);

            when(studentRepository.findById(404L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.updateStatus(404L, "ACTIVE", null)).isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Lookups")
    class Lookups {

        @Test
        void resolvingNoIdsCostsNothingAndTooManyIsRefused() {
            assertThat(service.findByIds(null)).isEmpty();
            assertThat(service.findByIds(Collections.emptyList())).isEmpty();
            verify(studentRepository, never()).findByIdIn(any());

            List<Long> tooMany = LongStream.rangeClosed(1, 1001).boxed().toList();
            assertThatThrownBy(() -> service.findByIds(tooMany)).isInstanceOf(BusinessRuleException.class);
        }

        @Test
        void countsCoverEveryStatusPlusATotal() {
            when(studentRepository.countByStatus(StudentStatus.ACTIVE)).thenReturn(5L);
            when(studentRepository.count()).thenReturn(9L);

            var counts = service.counts();

            assertThat(counts).containsEntry("ACTIVE", 5L).containsEntry("TOTAL", 9L);
            assertThat(counts.keySet()).contains("ALUMNI", "DROPPED", "SUSPENDED");
        }

        @Test
        void aProfileEditNormalisesBlankFieldsAndRejectsAnUnknownGender() {
            Student existing = Student.builder().id(88L).userId(99L).studentCode("STU-2026-000007").fullName("Priya Sharma")
                    .email("p@test.local").phone("9800000001").status(StudentStatus.ACTIVE).build();
            when(studentRepository.findById(88L)).thenReturn(Optional.of(existing));

            service.update(88L, new UpdateStudentRequest(null, "MALE", "  ", " Fergusson ", 2023, null, " Pune ", null, null, null, null, null, "   "));
            assertThat(existing.getCollege()).isEqualTo("Fergusson");
            assertThat(existing.getHighestEducation()).isNull();
            assertThat(existing.getRemarks()).isNull();

            assertThatThrownBy(() -> service.update(88L, new UpdateStudentRequest(null, "robot", null, null, null, null, null, null, null, null, null, null, null)))
                    .isInstanceOf(BusinessRuleException.class);
        }
    }
}
