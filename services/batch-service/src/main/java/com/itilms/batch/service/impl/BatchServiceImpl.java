package com.itilms.batch.service.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.batch.client.AdmissionClient;
import com.itilms.batch.client.CourseClient;
import com.itilms.batch.dto.request.CreateBatchRequest;
import com.itilms.batch.dto.request.EnrollStudentRequest;
import com.itilms.batch.dto.request.UpdateBatchRequest;
import com.itilms.batch.dto.response.BatchResponse;
import com.itilms.batch.dto.response.BatchSummaryResponse;
import com.itilms.batch.dto.response.EnrollmentResponse;
import com.itilms.batch.dto.response.EnrollmentResultResponse;
import com.itilms.batch.entity.Batch;
import com.itilms.batch.entity.BatchMode;
import com.itilms.batch.entity.BatchStatus;
import com.itilms.batch.entity.BatchTrainer;
import com.itilms.batch.entity.BatchTrainerRole;
import com.itilms.batch.entity.Enrollment;
import com.itilms.batch.entity.EnrollmentStatus;
import com.itilms.batch.repository.BatchRepository;
import com.itilms.batch.repository.BatchTrainerRepository;
import com.itilms.batch.repository.EnrollmentRepository;
import com.itilms.batch.service.BatchService;
import com.itilms.batch.specification.BatchSpecifications;
import com.itilms.common.dto.PageResponse;
import com.itilms.common.event.DomainEvent;
import com.itilms.common.event.EnrollmentCreatedEvent;
import com.itilms.common.event.EventPublisher;
import com.itilms.common.event.KafkaTopics;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.common.util.Codes;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchServiceImpl implements BatchService {

    private static final String SERVICE_NAME = "batch-service";
    private static final int CODE_RETRY_LIMIT = 5;

    private final BatchRepository batchRepository;
    private final BatchTrainerRepository batchTrainerRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseClient courseClient;
    private final AdmissionClient admissionClient;
    private final EventPublisher events;

    // -----------------------------------------------------------------
    // Read
    // -----------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BatchSummaryResponse> search(String status, Long courseId, Long trainerId,
                                                     String mode, String query, Pageable pageable) {
        Specification<Batch> spec = Specification.allOf(
                BatchSpecifications.hasStatus(status),
                BatchSpecifications.forCourse(courseId),
                BatchSpecifications.forTrainer(trainerId),
                BatchSpecifications.hasMode(mode),
                BatchSpecifications.matches(query));

        var page = batchRepository.findAll(spec, pageable);

        // Seat counts for the whole page in one query. Asking per row is how a
        // batch listing turns into twenty-one round trips.
        Map<Long, Long> counts = enrolledCounts(page.getContent().stream().map(Batch::getId).toList());

        return PageResponse.from(page, batch ->
                BatchSummaryResponse.from(batch, counts.getOrDefault(batch.getId(), 0L)));
    }

    @Override
    @Transactional(readOnly = true)
    public BatchResponse get(Long batchId) {
        Batch batch = require(batchId);
        long enrolled = enrollmentRepository.countByBatchIdAndStatus(batchId, EnrollmentStatus.ACTIVE);

        List<BatchResponse.CoTrainerResponse> coTrainers = batchTrainerRepository.findByBatchId(batchId)
                .stream()
                .map(bt -> new BatchResponse.CoTrainerResponse(
                        bt.getTrainerId(), bt.getTrainerName(), bt.getRole().name()))
                .toList();

        return BatchResponse.from(batch, enrolled, coTrainers);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EnrollmentResponse> roster(Long batchId) {
        require(batchId);
        return enrollmentRepository.findActiveRoster(batchId).stream()
                .map(EnrollmentResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BatchSummaryResponse> myBatches(Long studentId) {
        List<Long> batchIds = enrollmentRepository.findActiveForStudent(studentId).stream()
                .map(Enrollment::getBatchId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return summaries(batchIds);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BatchSummaryResponse> trainerBatches(Long trainerId) {
        return summaries(batchIdsForTrainer(trainerId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<BatchSummaryResponse> findByIds(Collection<Long> ids) {
        return summaries(ids);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> counts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (BatchStatus status : BatchStatus.values()) {
            counts.put(status.name(), batchRepository.countByStatus(status));
        }
        counts.put("TOTAL", batchRepository.count());
        counts.put("ACTIVE_ENROLLMENTS", enrollmentRepository.countByStatus(EnrollmentStatus.ACTIVE));
        return counts;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> activeBatchIdsForStudent(Long studentId) {
        return enrollmentRepository.findActiveForStudent(studentId).stream()
                .map(Enrollment::getBatchId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    /**
     * Every batch this trainer is attached to.
     *
     * <p>Combines the primary allocation on the batch row with co-trainer rows,
     * because a co-trainer teaching Thursday's session must be able to mark
     * Thursday's register.
     */
    @Override
    @Transactional(readOnly = true)
    public List<Long> batchIdsForTrainer(Long trainerId) {
        List<Long> primary = batchRepository
                .findByTrainerIdAndStatusIn(trainerId, List.of(BatchStatus.PLANNED, BatchStatus.ONGOING,
                        BatchStatus.COMPLETED))
                .stream().map(Batch::getId).toList();

        List<Long> shared = batchTrainerRepository.findBatchIdsForTrainer(trainerId);

        return java.util.stream.Stream.concat(primary.stream(), shared.stream()).distinct().toList();
    }

    @Override
    public boolean isActivelyEnrolled(Long batchId, Long studentId) {
        if (batchId == null || studentId == null) {
            return false;
        }
        return enrollmentRepository.existsByStudentIdAndBatchIdAndStatus(
                studentId, batchId, EnrollmentStatus.ACTIVE);
    }

    // -----------------------------------------------------------------
    // Write
    // -----------------------------------------------------------------

    @Override
    @Transactional
    public BatchResponse create(CreateBatchRequest request) {
        if (!request.endTime().isAfter(request.startTime())) {
            throw new BusinessRuleException("The batch's end time must be after its start time");
        }
        if (request.endDate() != null && request.endDate().isBefore(request.startDate())) {
            throw new BusinessRuleException("The batch's end date cannot be before its start date");
        }

        var course = courseClient.get(request.courseId());
        if (course == null || course.course() == null) {
            throw new BusinessRuleException(
                    "That course could not be found, so the batch was not created.");
        }
        var courseSummary = course.course();

        BatchMode mode = parseMode(request.mode());
        String classDays = normaliseClassDays(request.classDays());

        String trainerName = null;
        Long trainerUserId = null;
        if (request.trainerId() != null) {
            var trainers = admissionClient.lookupTrainers(List.of(request.trainerId()));
            if (!trainers.isEmpty()) {
                trainerName = trainers.get(0).fullName();
                trainerUserId = trainers.get(0).userId();
            }
        }

        Batch batch = persistWithGeneratedCode(request, courseSummary, mode, classDays,
                trainerName, trainerUserId);

        assignCoTrainers(batch.getId(), request.coTrainerIds());

        events.audit(SERVICE_NAME, "BATCH_CREATED", "Batch", batch.getId(), null,
                Map.of("batchCode", batch.getBatchCode(), "courseId", request.courseId()));

        log.info("Created batch {} ({}) for course {}",
                batch.getId(), batch.getBatchCode(), request.courseId());

        return get(batch.getId());
    }

    @Override
    @Transactional
    public BatchResponse update(Long batchId, UpdateBatchRequest request) {
        Batch batch = require(batchId);

        if (!request.endTime().isAfter(request.startTime())) {
            throw new BusinessRuleException("The batch's end time must be after its start time");
        }

        BatchStatus target = parseStatus(request.status());
        BatchStatus previous = batch.getStatus();

        long enrolled = enrollmentRepository.countByBatchIdAndStatus(batchId, EnrollmentStatus.ACTIVE);
        if (request.capacity() != null && request.capacity() < enrolled) {
            // Shrinking below the current roster would leave students in a batch
            // that officially cannot hold them.
            throw new BusinessRuleException(
                    "Capacity cannot be set to %d: %d student(s) are already enrolled."
                            .formatted(request.capacity(), enrolled));
        }

        BatchMode mode = parseMode(request.mode());
        if (mode == BatchMode.OFFLINE) {
            batch.setMeetingUrl(null);
        } else {
            batch.setMeetingUrl(trim(request.meetingUrl()));
        }

        if (request.trainerId() != null && !request.trainerId().equals(batch.getTrainerId())) {
            var trainers = admissionClient.lookupTrainers(List.of(request.trainerId()));
            if (!trainers.isEmpty()) {
                batch.setTrainerName(trainers.get(0).fullName());
                batch.setTrainerUserId(trainers.get(0).userId());
            }
            batch.setTrainerId(request.trainerId());
        }

        batch.setName(trim(request.name()));
        batch.setStartDate(request.startDate());
        batch.setEndDate(request.endDate());
        batch.setStartTime(request.startTime());
        batch.setEndTime(request.endTime());
        batch.setClassDays(normaliseClassDays(request.classDays()));
        batch.setMode(mode);
        if (request.capacity() != null) {
            batch.setCapacity(request.capacity());
        }
        batch.setClassroom(trim(request.classroom()));
        batch.setStatus(target);
        batchRepository.save(batch);

        if (request.coTrainerIds() != null) {
            assignCoTrainers(batchId, request.coTrainerIds());
        }

        if (previous != target) {
            events.audit(SERVICE_NAME, "BATCH_STATUS_CHANGED", "Batch", batchId,
                    Map.of("status", previous.name()), Map.of("status", target.name()));
            log.info("Batch {} status {} -> {}", batchId, previous, target);
        }

        return get(batchId);
    }

    // -----------------------------------------------------------------
    // Enrolment
    // -----------------------------------------------------------------

    @Override
    @Transactional
    public EnrollmentResultResponse enroll(Long batchId, EnrollStudentRequest request) {
        Batch batch = require(batchId);

        if (!batch.getStatus().acceptsEnrollment()) {
            throw new BusinessRuleException(
                    "This batch is %s and is not accepting enrolments."
                            .formatted(batch.getStatus().name().toLowerCase()));
        }

        List<Long> studentIds = request.studentIds().stream().filter(Objects::nonNull).distinct().toList();
        var students = admissionClient.lookupStudents(studentIds);
        Map<Long, AdmissionClient.StudentSummary> byId = new HashMap<>();
        students.forEach(student -> byId.put(student.id(), student));

        long taken = enrollmentRepository.countByBatchIdAndStatus(batchId, EnrollmentStatus.ACTIVE);
        long seatsLeft = batch.getCapacity() - taken;

        List<EnrollmentResultResponse.Outcome> outcomes = new ArrayList<>(studentIds.size());
        int enrolled = 0;

        for (Long studentId : studentIds) {
            var student = byId.get(studentId);
            if (student == null) {
                outcomes.add(EnrollmentResultResponse.Outcome.failed(studentId,
                        "No student record found"));
                continue;
            }

            // Doc S14: not twice in the same active batch.
            if (enrollmentRepository.existsByStudentIdAndBatchIdAndStatus(
                    studentId, batchId, EnrollmentStatus.ACTIVE)) {
                outcomes.add(EnrollmentResultResponse.Outcome.failed(studentId,
                        "%s is already enrolled in this batch".formatted(student.fullName())));
                continue;
            }

            if (seatsLeft <= 0) {
                outcomes.add(EnrollmentResultResponse.Outcome.failed(studentId,
                        "The batch is full (capacity %d)".formatted(batch.getCapacity())));
                continue;
            }

            Enrollment enrollment = enrollmentRepository.save(Enrollment.builder()
                    .studentId(studentId)
                    .userId(student.userId())
                    .studentCode(student.studentCode())
                    .studentName(student.fullName())
                    .courseId(batch.getCourseId())
                    .batchId(batchId)
                    .status(EnrollmentStatus.ACTIVE)
                    .build());

            seatsLeft--;
            enrolled++;
            outcomes.add(EnrollmentResultResponse.Outcome.ok(studentId));

            // course-service seeds the student's progress rows from this;
            // without it their lesson player has nowhere to record anything.
            events.publishAfterCommit(KafkaTopics.ENROLLMENT_CREATED, new EnrollmentCreatedEvent(
                    DomainEvent.newId(), Instant.now(),
                    enrollment.getId(), studentId, student.userId(),
                    batch.getCourseId(), batchId, batch.getBatchCode()));

            events.notifyUsers(List.of(student.userId()), "ENROLLMENT",
                    "You have been added to " + batch.getBatchCode(),
                    "Your classes start on %s at %s.".formatted(batch.getStartDate(), batch.getStartTime()),
                    "/student/batches/" + batchId);
        }

        events.audit(SERVICE_NAME, "STUDENTS_ENROLLED", "Batch", batchId, null,
                Map.of("requested", studentIds.size(), "enrolled", enrolled));

        log.info("Enrolled {} of {} student(s) into batch {}", enrolled, studentIds.size(), batchId);

        return new EnrollmentResultResponse(batchId, enrolled, studentIds.size() - enrolled,
                Math.max(0, seatsLeft), outcomes);
    }

    @Override
    @Transactional
    public EnrollmentResponse dropStudent(Long enrollmentId, String reason) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Enrollment", enrollmentId));

        if (!enrollment.getStatus().isActive()) {
            throw new BusinessRuleException("This enrolment is already " + enrollment.getStatus());
        }

        enrollment.drop(reason);
        enrollmentRepository.save(enrollment);

        events.publishAfterCommit(KafkaTopics.ENROLLMENT_CLOSED, new EnrollmentCreatedEvent(
                DomainEvent.newId(), Instant.now(),
                enrollment.getId(), enrollment.getStudentId(), enrollment.getUserId(),
                enrollment.getCourseId(), enrollment.getBatchId(), null));

        events.audit(SERVICE_NAME, "STUDENT_DROPPED", "Enrollment", enrollmentId,
                Map.of("status", EnrollmentStatus.ACTIVE.name()),
                Map.of("status", EnrollmentStatus.DROPPED.name(), "reason", String.valueOf(reason)));

        log.info("Dropped student {} from batch {}: {}",
                enrollment.getStudentId(), enrollment.getBatchId(), reason);

        return EnrollmentResponse.from(enrollment);
    }

    @Override
    @Transactional
    public EnrollmentResponse transferStudent(Long enrollmentId, Long targetBatchId) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Enrollment", enrollmentId));
        Batch target = require(targetBatchId);

        if (!enrollment.getStatus().isActive()) {
            throw new BusinessRuleException("Only an active enrolment can be transferred");
        }
        if (!target.getCourseId().equals(enrollment.getCourseId())) {
            // Transferring across courses would carry attendance and progress
            // from one syllabus into another, which is not a transfer at all.
            throw new BusinessRuleException(
                    "A student can only be transferred to another batch of the same course. "
                            + "Drop them and enrol them separately instead.");
        }
        if (!target.getStatus().acceptsEnrollment()) {
            throw new BusinessRuleException("The target batch is not accepting enrolments");
        }

        long taken = enrollmentRepository.countByBatchIdAndStatus(targetBatchId, EnrollmentStatus.ACTIVE);
        if (taken >= target.getCapacity()) {
            throw new BusinessRuleException("The target batch is full");
        }

        Long sourceBatchId = enrollment.getBatchId();
        enrollment.transfer(targetBatchId);
        enrollmentRepository.save(enrollment);

        Enrollment moved = enrollmentRepository.save(Enrollment.builder()
                .studentId(enrollment.getStudentId())
                .userId(enrollment.getUserId())
                .studentCode(enrollment.getStudentCode())
                .studentName(enrollment.getStudentName())
                .courseId(enrollment.getCourseId())
                .batchId(targetBatchId)
                .status(EnrollmentStatus.ACTIVE)
                .build());

        events.publishAfterCommit(KafkaTopics.ENROLLMENT_CREATED, new EnrollmentCreatedEvent(
                DomainEvent.newId(), Instant.now(),
                moved.getId(), moved.getStudentId(), moved.getUserId(),
                moved.getCourseId(), targetBatchId, target.getBatchCode()));

        events.notifyUsers(List.of(moved.getUserId()), "ENROLLMENT",
                "You have been moved to " + target.getBatchCode(),
                "Your classes now run at %s on %s.".formatted(target.getStartTime(), target.getClassDays()),
                "/student/batches/" + targetBatchId);

        events.audit(SERVICE_NAME, "STUDENT_TRANSFERRED", "Enrollment", enrollmentId,
                Map.of("batchId", String.valueOf(sourceBatchId)),
                Map.of("batchId", targetBatchId, "newEnrollmentId", moved.getId()));

        log.info("Transferred student {} from batch {} to {}",
                moved.getStudentId(), sourceBatchId, targetBatchId);

        return EnrollmentResponse.from(moved);
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private Batch require(Long batchId) {
        return batchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("Batch", batchId));
    }

    private List<BatchSummaryResponse> summaries(Collection<Long> batchIds) {
        if (batchIds == null || batchIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> counts = enrolledCounts(batchIds);
        return batchRepository.findByIdIn(batchIds).stream()
                .map(batch -> BatchSummaryResponse.from(batch, counts.getOrDefault(batch.getId(), 0L)))
                .toList();
    }

    private Map<Long, Long> enrolledCounts(Collection<Long> batchIds) {
        if (batchIds == null || batchIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> counts = new HashMap<>();
        enrollmentRepository.countActiveGroupedByBatch(batchIds)
                .forEach(row -> counts.put((Long) row[0], (Long) row[1]));
        return counts;
    }

    private void assignCoTrainers(Long batchId, List<Long> trainerIds) {
        batchTrainerRepository.deleteByBatchId(batchId);
        if (trainerIds == null || trainerIds.isEmpty()) {
            return;
        }
        List<Long> distinct = trainerIds.stream().filter(Objects::nonNull).distinct().toList();
        var trainers = admissionClient.lookupTrainers(distinct);
        Map<Long, AdmissionClient.TrainerSummary> byId = new HashMap<>();
        trainers.forEach(trainer -> byId.put(trainer.id(), trainer));

        List<BatchTrainer> rows = distinct.stream()
                .map(trainerId -> {
                    var trainer = byId.get(trainerId);
                    return BatchTrainer.builder()
                            .batchId(batchId)
                            .trainerId(trainerId)
                            .trainerUserId(trainer == null ? null : trainer.userId())
                            .trainerName(trainer == null ? null : trainer.fullName())
                            .role(BatchTrainerRole.CO_TRAINER)
                            .build();
                })
                .toList();
        batchTrainerRepository.saveAll(rows);
    }

    /**
     * Saves the batch, retrying if the generated code collides.
     *
     * <p>Same pattern as student codes: the counter comes from a COUNT, so two
     * batches created at once can compute the same code. The unique constraint
     * catches it and the loop recomputes.
     */
    private Batch persistWithGeneratedCode(CreateBatchRequest request,
                                           CourseClient.CourseSummary course,
                                           BatchMode mode, String classDays,
                                           String trainerName, Long trainerUserId) {
        for (int attempt = 1; attempt <= CODE_RETRY_LIMIT; attempt++) {
            long sequence = batchRepository.nextSequenceForCourse(request.courseId()) + attempt - 1;
            String code = Codes.batchCode(course.code() == null ? "BATCH" : course.code(), sequence);

            try {
                return batchRepository.saveAndFlush(Batch.builder()
                        .batchCode(code)
                        .name(trim(request.name()))
                        .courseId(request.courseId())
                        .courseCode(course.code())
                        .courseTitle(course.title())
                        .trainerId(request.trainerId())
                        .trainerUserId(trainerUserId)
                        .trainerName(trainerName)
                        .startDate(request.startDate())
                        .endDate(request.endDate())
                        .startTime(request.startTime())
                        .endTime(request.endTime())
                        .classDays(classDays)
                        .mode(mode)
                        .capacity(request.capacity() == null ? 30 : request.capacity())
                        .classroom(trim(request.classroom()))
                        .meetingUrl(mode == BatchMode.OFFLINE ? null : trim(request.meetingUrl()))
                        .status(BatchStatus.PLANNED)
                        .build());
            } catch (DataIntegrityViolationException collision) {
                if (attempt == CODE_RETRY_LIMIT) {
                    throw collision;
                }
                log.debug("Batch code {} was taken; retrying (attempt {})", code, attempt + 1);
            }
        }
        throw new IllegalStateException("Unreachable: the retry loop always returns or rethrows");
    }

    /** Validates and canonicalises the weekday list to {@code MON,WED,FRI}. */
    private String normaliseClassDays(List<String> days) {
        if (days == null || days.isEmpty()) {
            return "MON,TUE,WED,THU,FRI";
        }
        List<String> tokens = days.stream()
                .filter(Objects::nonNull)
                .map(day -> day.trim().toUpperCase())
                .map(day -> day.length() > 3 ? day.substring(0, 3) : day)
                .distinct()
                .toList();

        List<String> invalid = tokens.stream()
                .filter(token -> !Batch.VALID_DAY_TOKENS.contains(token))
                .toList();
        if (!invalid.isEmpty()) {
            throw new BusinessRuleException(
                    "Unrecognised class day(s): %s. Use MON, TUE, WED, THU, FRI, SAT or SUN."
                            .formatted(String.join(", ", invalid)));
        }

        // Kept in weekday order regardless of how they were submitted, so a
        // timetable never reads "FRI,MON,WED".
        return Batch.VALID_DAY_TOKENS.stream()
                .filter(tokens::contains)
                .reduce((a, b) -> a + "," + b)
                .orElse("MON,TUE,WED,THU,FRI");
    }

    private BatchMode parseMode(String value) {
        if (value == null || value.isBlank()) {
            return BatchMode.OFFLINE;
        }
        try {
            return BatchMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("Mode must be ONLINE, OFFLINE or HYBRID");
        }
    }

    private BatchStatus parseStatus(String value) {
        try {
            return BatchStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new BusinessRuleException("Status must be PLANNED, ONGOING, COMPLETED or CANCELLED");
        }
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
