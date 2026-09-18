package com.itilms.admission.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.admission.client.IdentityClient;
import com.itilms.admission.dto.request.CreateStudentRequest;
import com.itilms.admission.dto.request.UpdateStudentRequest;
import com.itilms.admission.dto.response.StudentResponse;
import com.itilms.admission.dto.response.StudentSummaryResponse;
import com.itilms.admission.entity.Gender;
import com.itilms.admission.entity.LeadSource;
import com.itilms.admission.entity.Student;
import com.itilms.admission.entity.StudentStatus;
import com.itilms.admission.repository.StudentRepository;
import com.itilms.admission.service.StudentService;
import com.itilms.admission.specification.StudentSpecifications;
import com.itilms.common.dto.PageResponse;
import com.itilms.common.event.DomainEvent;
import com.itilms.common.event.EventPublisher;
import com.itilms.common.event.KafkaTopics;
import com.itilms.common.event.ProfileLinkedEvent;
import com.itilms.common.event.StudentAdmittedEvent;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.common.util.Codes;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentServiceImpl implements StudentService {

    private static final String SERVICE_NAME = "admission-service";
    private static final int CODE_RETRY_LIMIT = 5;

    private final StudentRepository studentRepository;
    private final IdentityClient identityClient;
    private final EventPublisher events;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StudentSummaryResponse> search(String status, String query, Pageable pageable) {
        Specification<Student> spec = Specification.allOf(
                StudentSpecifications.hasStatus(status),
                StudentSpecifications.matches(query));
        return PageResponse.from(studentRepository.findAll(spec, pageable), StudentSummaryResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public StudentResponse get(Long id) {
        return studentRepository.findById(id)
                .map(StudentResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Student", id));
    }

    @Override
    @Transactional(readOnly = true)
    public StudentResponse getByUserId(Long userId) {
        return studentRepository.findByUserId(userId)
                .map(StudentResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No student profile is linked to this account"));
    }

    /**
     * Admission in one transaction from this service's point of view.
     *
     * <p>The account is created first, through a synchronous call, because the
     * profile cannot exist without a user id to point at. If that call fails the
     * transaction rolls back and nothing is left behind — see
     * {@code IdentityClientFallback} for why it is allowed to fail loudly.
     *
     * <p>The reverse case — account created, then this transaction rolls back —
     * leaves an orphaned account with no profile. That is recoverable (the
     * account is inert, and re-running the admission links it) and is the lesser
     * of the two failures, which is why the ordering is this way round.
     */
    @Override
    @Transactional
    public StudentResponse create(CreateStudentRequest request, LeadSource source, AdmissionTerms terms) {
        String email = request.email().trim().toLowerCase();

        var account = identityClient.createUser(IdentityClient.CreateUserPayload.withGeneratedPassword(
                request.firstName().trim(), request.lastName().trim(), email,
                request.phone().trim(), "STUDENT"));

        if (studentRepository.existsByUserId(account.id())) {
            throw new BusinessRuleException(
                    "A student profile already exists for this account. Open it instead of creating a new one.");
        }

        Student student = persistWithGeneratedCode(request, source, account.id(),
                request.firstName().trim() + " " + request.lastName().trim(), email);

        log.info("Admitted student {} ({}) on account {}",
                student.getId(), student.getStudentCode(), account.id());

        // Lets identity-service put the student id into future access tokens,
        // so every ownership check downstream has it without a lookup.
        events.publishAfterCommit(KafkaTopics.PROFILE_LINKED,
                ProfileLinkedEvent.of(account.id(), "STUDENT", student.getId(), student.getStudentCode()));

        // Starts the rest of the admission workflow: fee plan, batch enrolment,
        // welcome message. Carries whatever terms were agreed; when none were,
        // the nulls tell finance-service there is no plan to raise yet.
        events.publishAfterCommit(KafkaTopics.STUDENT_ADMITTED, new StudentAdmittedEvent(
                DomainEvent.newId(), Instant.now(),
                student.getId(), account.id(), student.getStudentCode(),
                student.getFullName(), student.getEmail(), student.getPhone(),
                terms.courseId(), terms.batchId(), student.getAdmissionDate(),
                com.itilms.common.security.SecurityUtils.currentUserId(),
                terms.totalFee(), terms.discount(), terms.installments()));

        events.audit(SERVICE_NAME, "STUDENT_ADMITTED", "Student", student.getId(), null,
                Map.of("studentCode", student.getStudentCode(),
                        "email", email,
                        "courseId", String.valueOf(terms.courseId()),
                        "totalFee", String.valueOf(terms.totalFee())));

        return StudentResponse.from(student);
    }

    @Override
    @Transactional
    public StudentResponse update(Long id, UpdateStudentRequest request) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student", id));

        student.setDateOfBirth(request.dateOfBirth());
        student.setGender(parseGender(request.gender()));
        student.setHighestEducation(trim(request.highestEducation()));
        student.setCollege(trim(request.college()));
        student.setGraduationYear(request.graduationYear());
        student.setAddressLine(trim(request.addressLine()));
        student.setCity(trim(request.city()));
        student.setState(trim(request.state()));
        student.setPincode(trim(request.pincode()));
        student.setGuardianName(trim(request.guardianName()));
        student.setGuardianPhone(trim(request.guardianPhone()));
        student.setEmergencyContact(trim(request.emergencyContact()));
        student.setRemarks(trim(request.remarks()));

        studentRepository.save(student);
        events.audit(SERVICE_NAME, "STUDENT_UPDATED", "Student", id, null, null);

        return StudentResponse.from(student);
    }

    @Override
    @Transactional
    public StudentResponse updateStatus(Long id, String status, String reason) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student", id));

        StudentStatus target = parseStatus(status);
        StudentStatus previous = student.getStatus();
        if (previous == target) {
            return StudentResponse.from(student);
        }

        student.setStatus(target);
        studentRepository.save(student);

        events.audit(SERVICE_NAME, "STUDENT_STATUS_CHANGED", "Student", id,
                Map.of("status", previous.name()),
                Map.of("status", target.name(), "reason", reason == null ? "" : reason));

        log.info("Student {} status {} -> {}", id, previous, target);
        return StudentResponse.from(student);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentSummaryResponse> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        if (ids.size() > 1000) {
            throw new BusinessRuleException("At most 1000 student ids may be resolved in one call");
        }
        return studentRepository.findByIdIn(ids).stream().map(StudentSummaryResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> counts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (StudentStatus status : StudentStatus.values()) {
            counts.put(status.name(), studentRepository.countByStatus(status));
        }
        counts.put("TOTAL", studentRepository.count());
        return counts;
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    /**
     * Saves the profile, retrying if the generated student code collides.
     *
     * <p>The code embeds a per-year counter derived from a COUNT, so two
     * admissions committed in the same instant can compute the same value. The
     * unique constraint catches it; this loop recomputes and tries again rather
     * than showing a coordinator a database error for something the system can
     * resolve itself.
     */
    private Student persistWithGeneratedCode(CreateStudentRequest request, LeadSource source,
                                             Long userId, String fullName, String email) {
        LocalDate admissionDate = request.admissionDate() == null ? LocalDate.now() : request.admissionDate();

        for (int attempt = 1; attempt <= CODE_RETRY_LIMIT; attempt++) {
            long sequence = studentRepository.nextCodeSequence(admissionDate.getYear()) + attempt - 1;
            String code = Codes.studentCode(sequence);

            try {
                return studentRepository.saveAndFlush(Student.builder()
                        .userId(userId)
                        .studentCode(code)
                        .fullName(fullName)
                        .email(email)
                        .phone(request.phone().trim())
                        .dateOfBirth(request.dateOfBirth())
                        .gender(parseGender(request.gender()))
                        .highestEducation(trim(request.highestEducation()))
                        .college(trim(request.college()))
                        .graduationYear(request.graduationYear())
                        .addressLine(trim(request.addressLine()))
                        .city(trim(request.city()))
                        .state(trim(request.state()))
                        .pincode(trim(request.pincode()))
                        .guardianName(trim(request.guardianName()))
                        .guardianPhone(trim(request.guardianPhone()))
                        .emergencyContact(trim(request.emergencyContact()))
                        .admissionDate(admissionDate)
                        .admissionSource(source)
                        .status(StudentStatus.ACTIVE)
                        .remarks(trim(request.remarks()))
                        .build());
            } catch (DataIntegrityViolationException collision) {
                if (attempt == CODE_RETRY_LIMIT) {
                    throw collision;
                }
                log.debug("Student code {} was taken; retrying (attempt {})", code, attempt + 1);
            }
        }
        throw new IllegalStateException("Unreachable: the retry loop always returns or rethrows");
    }

    private Gender parseGender(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Gender.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("Gender must be MALE, FEMALE or OTHER");
        }
    }

    private StudentStatus parseStatus(String value) {
        try {
            return StudentStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new BusinessRuleException("Status must be ACTIVE, ALUMNI, DROPPED or SUSPENDED");
        }
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
