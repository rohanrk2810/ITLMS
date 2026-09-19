package com.itilms.admission.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import com.itilms.admission.entity.LeadSource;
import com.itilms.admission.entity.Student;
import com.itilms.admission.entity.StudentStatus;
import com.itilms.admission.repository.StudentRepository;
import com.itilms.admission.repository.TrainerRepository;
import com.itilms.common.event.KafkaTopics;
import com.itilms.common.event.ProfileLinkedEvent;
import com.itilms.common.event.UserCreatedEvent;

/** Kafka delivers at least once, so every branch here has to be safe to run twice. */
@ExtendWith(MockitoExtension.class)
class UserEventConsumerTest {

    @Mock StudentRepository studentRepository;
    @Mock TrainerRepository trainerRepository;
    @Mock KafkaTemplate<String, Object> kafkaTemplate;

    UserEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new UserEventConsumer(studentRepository, trainerRepository, kafkaTemplate);
    }

    private static UserCreatedEvent event(String role, boolean selfRegistered) {
        return new UserCreatedEvent("ev-1", Instant.now(), 42L, "ravi@test.local", "9800000002", "Ravi Kumar", role, selfRegistered);
    }

    @Test
    void aSelfRegisteredStudentGetsAProfileAndTheLinkIsAnnounced() {
        when(studentRepository.existsByUserId(42L)).thenReturn(false);
        when(studentRepository.nextCodeSequence(anyInt())).thenReturn(3L);
        when(studentRepository.save(any(Student.class))).thenAnswer(i -> {
            Student s = i.getArgument(0);
            s.setId(77L);
            return s;
        });

        consumer.onUserCreated(event("STUDENT", true));

        ArgumentCaptor<Student> saved = ArgumentCaptor.forClass(Student.class);
        verify(studentRepository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(42L);
        assertThat(saved.getValue().getAdmissionSource()).isEqualTo(LeadSource.SELF_REGISTRATION);
        assertThat(saved.getValue().getStatus()).isEqualTo(StudentStatus.ACTIVE);
        assertThat(saved.getValue().getStudentCode()).endsWith("-000003");

        ArgumentCaptor<Object> linked = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.PROFILE_LINKED), anyString(), linked.capture());
        assertThat(((ProfileLinkedEvent) linked.getValue()).profileId()).isEqualTo(77L);
    }

    @Test
    void aRedeliveredEventDoesNotCreateASecondProfile() {
        when(studentRepository.existsByUserId(42L)).thenReturn(true);

        consumer.onUserCreated(event("STUDENT", true));

        verify(studentRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void anAccountAdmissionCreatedItselfIsLeftAlone() {
        // Staff-created students get their profile from the admission flow, not from this listener.
        consumer.onUserCreated(event("STUDENT", false));

        verify(studentRepository, never()).save(any());
    }

    @Test
    void nonStudentAccountsNeverGetAStudentProfile() {
        consumer.onUserCreated(event("TRAINER", true));
        consumer.onUserCreated(event("ADMIN", true));

        verify(studentRepository, never()).save(any());
    }

    @Test
    void anIdentityChangeIsCopiedToBothStudentAndTrainerRecords() {
        when(studentRepository.syncIdentity(42L, "Ravi Kumar", "ravi@test.local", "9800000002")).thenReturn(1);
        when(trainerRepository.syncIdentity(42L, "Ravi Kumar", "ravi@test.local", "9800000002")).thenReturn(0);

        consumer.onUserUpdated(event("STUDENT", false));

        verify(studentRepository).syncIdentity(42L, "Ravi Kumar", "ravi@test.local", "9800000002");
        verify(trainerRepository).syncIdentity(42L, "Ravi Kumar", "ravi@test.local", "9800000002");
    }
}
