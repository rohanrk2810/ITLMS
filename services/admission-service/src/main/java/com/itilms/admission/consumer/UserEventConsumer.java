package com.itilms.admission.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.admission.entity.Gender;
import com.itilms.admission.entity.LeadSource;
import com.itilms.admission.entity.Student;
import com.itilms.admission.entity.StudentStatus;
import com.itilms.admission.repository.StudentRepository;
import com.itilms.admission.repository.TrainerRepository;
import com.itilms.common.event.DomainEvent;
import com.itilms.common.event.KafkaTopics;
import com.itilms.common.event.NotificationRequestedEvent;
import com.itilms.common.event.ProfileLinkedEvent;
import com.itilms.common.event.StudentDetails;
import com.itilms.common.event.UserCreatedEvent;
import com.itilms.common.util.Codes;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Reacts to account changes in identity-service.
 *
 * <p>Two jobs:
 *
 * <ul>
 *   <li><b>Self-registration.</b> A visitor who signs up on the public site gets
 *       an account but no student record, and without one they cannot be
 *       enrolled, marked present, or charged a fee. This creates the missing
 *       profile so the account is usable the moment staff get to it.</li>
 *   <li><b>Name and contact changes.</b> Student and trainer rows keep a copy of
 *       the display name so rosters render in one query. This is what keeps the
 *       copy honest.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventConsumer {

    private final StudentRepository studentRepository;
    private final TrainerRepository trainerRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Creates a profile for a self-registered student.
     *
     * <p>Only self-registered STUDENT accounts are handled. Staff-created
     * students and trainers already have a profile — the request that created
     * the account created it too — so acting on those here would produce a
     * duplicate. The event says which kind of account this is; see
     * {@link UserCreatedEvent#selfRegistered()} for why that is not inferred
     * from the database.
     */
    @KafkaListener(topics = KafkaTopics.USER_CREATED, groupId = "admission-service")
    @Transactional
    public void onUserCreated(UserCreatedEvent event) {
        if (!event.selfRegistered() || !"STUDENT".equals(event.role())) {
            return;
        }
        // Still guarded: Kafka delivers at least once, so a redelivered event
        // for an account already handled must be a no-op.
        if (studentRepository.existsByUserId(event.userId())) {
            log.debug("Student profile already exists for user {}; nothing to do", event.userId());
            return;
        }

        long sequence = studentRepository.nextCodeSequence(java.time.Year.now().getValue());
        Student student = Student.builder()
                .userId(event.userId())
                .studentCode(Codes.studentCode(sequence))
                .fullName(event.fullName())
                .email(event.email())
                .phone(event.phone())
                .admissionSource(LeadSource.SELF_REGISTRATION)
                .status(StudentStatus.ACTIVE)
                .build();
        applyDetails(student, event.studentDetails());
        student = studentRepository.save(student);

        log.info("Created profile {} ({}) for self-registered user {}",
                student.getId(), student.getStudentCode(), event.userId());

        // Sent directly rather than through EventPublisher's after-commit hook:
        // this runs inside a Kafka listener, and the ordering guarantee that
        // matters here is simply that the row is written before the link is
        // announced, which @Transactional on this method already provides.
        kafkaTemplate.send(KafkaTopics.PROFILE_LINKED, DomainEvent.newId(),
                ProfileLinkedEvent.of(event.userId(), "STUDENT",
                        student.getId(), student.getStudentCode()));

        // The new student sees only the course list until a course request is approved; tell the people who decide.
        String message = "%s (%s) has registered and is waiting to be placed in a course. Approve their course request, "
                .formatted(student.getFullName(), student.getStudentCode()) + "then set up their fee plan.";
        for (String role : new String[]{"ADMIN", "COORDINATOR"}) {
            kafkaTemplate.send(KafkaTopics.NOTIFICATION_REQUESTED, DomainEvent.newId(),
                    NotificationRequestedEvent.toRole(role, "NEW_STUDENT", "New student: " + student.getFullName(),
                            message, "/admin/students/" + student.getId()));
        }
    }

    /** The details typed on the registration form. Absent for Google and WhatsApp sign-ups, which cannot ask. */
    private static void applyDetails(Student student, StudentDetails details) {
        if (details == null) {
            return;
        }
        student.setDateOfBirth(details.dateOfBirth());
        student.setGender(details.gender() == null ? null : Gender.valueOf(details.gender().trim().toUpperCase()));
        student.setHighestEducation(trim(details.highestEducation()));
        student.setCollege(trim(details.college()));
        student.setGraduationYear(details.graduationYear());
        student.setAddressLine(trim(details.addressLine()));
        student.setCity(trim(details.city()));
        student.setState(trim(details.state()));
        student.setPincode(trim(details.pincode()));
        student.setGuardianName(trim(details.guardianName()));
        student.setGuardianPhone(trim(details.guardianPhone()));
        student.setEmergencyContact(trim(details.emergencyContact()));
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Keeps the denormalised name, email and phone in step with the account. */
    @KafkaListener(topics = KafkaTopics.USER_UPDATED, groupId = "admission-service")
    @Transactional
    public void onUserUpdated(UserCreatedEvent event) {
        int students = studentRepository.syncIdentity(
                event.userId(), event.fullName(), event.email(), event.phone());
        int trainers = trainerRepository.syncIdentity(
                event.userId(), event.fullName(), event.email(), event.phone());

        if (students + trainers > 0) {
            log.debug("Refreshed cached identity for user {} ({} student, {} trainer row(s))",
                    event.userId(), students, trainers);
        }
    }
}
