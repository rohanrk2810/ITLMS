package com.itilms.assessment.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.assessment.client.BatchClient;
import com.itilms.assessment.dto.request.CreateAssignmentRequest;
import com.itilms.assessment.dto.request.EvaluateSubmissionRequest;
import com.itilms.assessment.dto.request.SubmitAssignmentRequest;
import com.itilms.assessment.dto.response.AssignmentResponse;
import com.itilms.assessment.dto.response.SubmissionResponse;
import com.itilms.assessment.entity.Assignment;
import com.itilms.assessment.entity.AssignmentStatus;
import com.itilms.assessment.entity.Submission;
import com.itilms.assessment.entity.SubmissionFile;
import com.itilms.assessment.entity.SubmissionStatus;
import com.itilms.assessment.repository.AssignmentRepository;
import com.itilms.assessment.repository.SubmissionRepository;
import com.itilms.assessment.service.AssessmentAccess;
import com.itilms.assessment.service.AssignmentService;
import com.itilms.common.dto.PageResponse;
import com.itilms.common.event.AssignmentCreatedEvent;
import com.itilms.common.event.DomainEvent;
import com.itilms.common.event.EventPublisher;
import com.itilms.common.event.KafkaTopics;
import com.itilms.common.event.SubmissionEvaluatedEvent;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.ForbiddenOperationException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.common.security.AppPrincipal;
import com.itilms.common.security.SecurityUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssignmentServiceImpl implements AssignmentService {

    private static final String SERVICE_NAME = "assessment-service";

    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final AssessmentAccess access;
    private final EventPublisher events;

    // -----------------------------------------------------------------
    // Setting work
    // -----------------------------------------------------------------

    @Override
    @Transactional
    public AssignmentResponse create(CreateAssignmentRequest request) {
        AppPrincipal caller = access.requireManagesBatch(request.batchId());

        Assignment assignment = Assignment.builder()
                .batchId(request.batchId())
                .courseId(request.courseId() != null ? request.courseId() : courseOfBatch(request.batchId()))
                .title(request.title().trim())
                .instructions(trim(request.instructions()))
                .attachmentRef(trim(request.attachmentRef()))
                .dueAt(request.dueAt())
                .maxMarks(request.maxMarks())
                .allowLate(request.allowLate() == null || request.allowLate())
                .mandatory(request.mandatory() == null || request.mandatory())
                .trainerId(caller.trainerIdOrNull())
                .status(AssignmentStatus.DRAFT)
                .build();

        if (!Boolean.TRUE.equals(request.draft())) {
            assignment.publish(Instant.now());
        }
        assignment = assignmentRepository.save(assignment);

        if (assignment.getStatus() == AssignmentStatus.PUBLISHED) {
            announce(assignment);
        }
        events.audit(SERVICE_NAME, "ASSIGNMENT_CREATED", "Assignment", assignment.getId(), null,
                Map.of("batchId", assignment.getBatchId(), "status", assignment.getStatus().name()));
        return AssignmentResponse.from(assignment);
    }

    @Override
    @Transactional
    public AssignmentResponse update(Long id, CreateAssignmentRequest request) {
        Assignment assignment = requireAssignment(id);
        access.requireManagesBatch(assignment.getBatchId());

        if (!assignment.getBatchId().equals(request.batchId())) {
            throw new BusinessRuleException(
                    "An assignment cannot be moved to another batch. Create it again for that batch.");
        }

        // Lowering the maximum below a mark already given would leave a student
        // with 18 out of 15. Refuse rather than quietly clip their mark.
        int highestAwarded = submissionRepository.findByAssignmentIdOrderBySubmittedAtAsc(id).stream()
                .map(Submission::getMarks).filter(java.util.Objects::nonNull)
                .max(Integer::compare).orElse(0);
        if (request.maxMarks() < highestAwarded) {
            throw new BusinessRuleException("A submission has already been awarded %d marks, so the maximum "
                    .formatted(highestAwarded) + "cannot be set below that.");
        }

        assignment.setTitle(request.title().trim());
        assignment.setInstructions(trim(request.instructions()));
        assignment.setAttachmentRef(trim(request.attachmentRef()));
        assignment.setDueAt(request.dueAt());
        assignment.setMaxMarks(request.maxMarks());
        if (request.allowLate() != null) {
            assignment.setAllowLate(request.allowLate());
        }
        if (request.mandatory() != null) {
            assignment.setMandatory(request.mandatory());
        }
        return AssignmentResponse.from(assignmentRepository.save(assignment));
    }

    @Override
    @Transactional
    public AssignmentResponse publish(Long id) {
        Assignment assignment = requireAssignment(id);
        access.requireManagesBatch(assignment.getBatchId());
        if (assignment.getStatus() != AssignmentStatus.DRAFT) {
            throw new BusinessRuleException("Only a draft assignment can be published.");
        }
        assignment.publish(Instant.now());
        assignment = assignmentRepository.save(assignment);
        announce(assignment);
        return AssignmentResponse.from(assignment);
    }

    @Override
    @Transactional
    public AssignmentResponse close(Long id) {
        Assignment assignment = requireAssignment(id);
        access.requireManagesBatch(assignment.getBatchId());
        if (assignment.getStatus() != AssignmentStatus.PUBLISHED) {
            throw new BusinessRuleException("Only a published assignment can be closed.");
        }
        assignment.setStatus(AssignmentStatus.CLOSED);
        return AssignmentResponse.from(assignmentRepository.save(assignment));
    }

    // -----------------------------------------------------------------
    // Reading
    // -----------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public AssignmentResponse get(Long id) {
        Assignment assignment = requireAssignment(id);
        AppPrincipal caller = SecurityUtils.requirePrincipal();

        if (caller.isStudent()) {
            if (assignment.getStatus() == AssignmentStatus.DRAFT) {
                throw new ResourceNotFoundException("Assignment", id);
            }
            access.requireEnrolled(assignment.getBatchId(), caller.profileId());
            SubmissionResponse mine = submissionRepository
                    .findByAssignmentIdAndStudentId(id, caller.profileId())
                    .map(SubmissionResponse::from).orElse(null);
            return AssignmentResponse.of(assignment, null, null, mine);
        }

        access.requireManagesBatch(assignment.getBatchId());
        long[] counts = countsFor(List.of(id)).getOrDefault(id, new long[]{0, 0});
        return AssignmentResponse.of(assignment, counts[0], counts[1], null);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AssignmentResponse> forBatch(Long batchId, Pageable pageable) {
        access.requireManagesBatch(batchId);
        Page<Assignment> page = assignmentRepository.findByBatchIdOrderByDueAtDesc(batchId, pageable);
        Map<Long, long[]> counts = countsFor(page.map(Assignment::getId).getContent());
        return PageResponse.from(page, a -> {
            long[] c = counts.getOrDefault(a.getId(), new long[]{0, 0});
            return AssignmentResponse.of(a, c[0], c[1], null);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignmentResponse> mine() {
        AppPrincipal student = access.requireStudent();
        List<Long> batchIds = access.myBatches().stream().map(BatchClient.BatchSummary::id).toList();
        if (batchIds.isEmpty()) {
            return List.of();
        }

        List<Assignment> assignments = assignmentRepository
                .findByBatchIdInAndStatusOrderByDueAtAsc(batchIds, AssignmentStatus.PUBLISHED);
        Map<Long, Submission> own = submissionRepository
                .findByStudentIdAndAssignmentIdIn(student.profileId(),
                        assignments.stream().map(Assignment::getId).toList())
                .stream().collect(Collectors.toMap(Submission::getAssignmentId, Function.identity()));

        return assignments.stream()
                .map(a -> AssignmentResponse.of(a, null, null,
                        own.containsKey(a.getId()) ? SubmissionResponse.from(own.get(a.getId())) : null))
                .toList();
    }

    // -----------------------------------------------------------------
    // Handing work in
    // -----------------------------------------------------------------

    @Override
    @Transactional
    public SubmissionResponse submit(Long assignmentId, SubmitAssignmentRequest request) {
        AppPrincipal student = access.requireStudent();
        Assignment assignment = requireAssignment(assignmentId);
        if (assignment.getStatus() == AssignmentStatus.DRAFT) {
            throw new ResourceNotFoundException("Assignment", assignmentId);
        }
        access.requireEnrolled(assignment.getBatchId(), student.profileId());

        boolean hasText = request.textAnswer() != null && !request.textAnswer().isBlank();
        boolean hasFiles = request.files() != null && !request.files().isEmpty();
        if (!hasText && !hasFiles) {
            throw new BusinessRuleException("A submission needs a written answer, a file, or both.");
        }

        Instant now = Instant.now();
        if (!assignment.acceptsSubmissionAt(now)) {
            throw new BusinessRuleException(assignment.getStatus() == AssignmentStatus.CLOSED
                    ? "This assignment is closed and no longer accepts submissions."
                    : "The deadline has passed and this assignment does not accept late work.");
        }
        boolean late = assignment.isOverdue(now);

        Submission submission = submissionRepository
                .findByAssignmentIdAndStudentId(assignmentId, student.profileId())
                .orElse(null);

        if (submission == null) {
            submission = Submission.builder()
                    .assignmentId(assignmentId)
                    .studentId(student.profileId())
                    .studentUserId(student.userId())
                    .studentName(student.fullName())
                    .textAnswer(hasText ? request.textAnswer() : null)
                    .submittedAt(now)
                    .status(late ? SubmissionStatus.LATE : SubmissionStatus.SUBMITTED)
                    .build();
        } else if (!submission.getStatus().allowsResubmission()) {
            // Replacing marked work would leave the mark describing something
            // the trainer never saw.
            throw new BusinessRuleException(
                    "This work has already been marked. Ask your trainer to return it if it needs redoing.");
        } else {
            submission.resubmit(hasText ? request.textAnswer() : null, now, late);
        }

        if (hasFiles) {
            for (SubmitAssignmentRequest.FileRef f : request.files()) {
                submission.addFile(SubmissionFile.builder()
                        .fileRef(f.fileRef()).fileName(f.fileName())
                        .contentType(f.contentType()).sizeBytes(f.sizeBytes())
                        .uploadedAt(now).build());
            }
        }

        submission = submissionRepository.save(submission);
        log.info("Student {} submitted assignment {} ({}, version {})", student.profileId(), assignmentId,
                submission.getStatus(), submission.getSubmissionCount());
        return SubmissionResponse.from(submission);
    }

    // -----------------------------------------------------------------
    // Marking
    // -----------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<SubmissionResponse> submissions(Long assignmentId) {
        Assignment assignment = requireAssignment(assignmentId);
        access.requireManagesBatch(assignment.getBatchId());
        return submissionRepository.findByAssignmentIdOrderBySubmittedAtAsc(assignmentId).stream()
                .map(SubmissionResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubmissionResponse> awaitingEvaluation(Long batchId) {
        access.requireManagesBatch(batchId);
        List<Long> assignmentIds = assignmentRepository
                .findByBatchIdOrderByDueAtDesc(batchId, Pageable.unpaged())
                .map(Assignment::getId).getContent();
        if (assignmentIds.isEmpty()) {
            return List.of();
        }
        return submissionRepository.findAwaitingEvaluation(assignmentIds).stream()
                .map(SubmissionResponse::from).toList();
    }

    @Override
    @Transactional
    public SubmissionResponse evaluate(Long submissionId, EvaluateSubmissionRequest request) {
        Submission submission = requireSubmission(submissionId);
        Assignment assignment = requireAssignment(submission.getAssignmentId());
        AppPrincipal trainer = access.requireManagesBatch(assignment.getBatchId());
        Instant now = Instant.now();

        Map<String, Object> before = new HashMap<>();
        before.put("status", submission.getStatus().name());
        before.put("marks", submission.getMarks());

        if (Boolean.TRUE.equals(request.returnForRework())) {
            if (request.feedback() == null || request.feedback().isBlank()) {
                throw new BusinessRuleException("Say what needs redoing when returning work.");
            }
            submission.returnForRework(request.feedback().trim(), trainer.userId(), now);
        } else {
            if (request.marks() == null) {
                throw new BusinessRuleException("Marks are required to evaluate a submission.");
            }
            if (request.marks() > assignment.getMaxMarks()) {
                throw new BusinessRuleException("Marks cannot exceed the maximum of %d."
                        .formatted(assignment.getMaxMarks()));
            }
            submission.evaluate(request.marks(), trim(request.feedback()), trainer.userId(), now);
        }
        submission = submissionRepository.save(submission);

        events.publishAfterCommit(KafkaTopics.SUBMISSION_EVALUATED, new SubmissionEvaluatedEvent(
                DomainEvent.newId(), now, submission.getId(), assignment.getId(), assignment.getBatchId(),
                submission.getStudentId(), submission.getStudentUserId(),
                submission.getMarks() == null ? null : BigDecimal.valueOf(submission.getMarks()),
                assignment.getMaxMarks(), submission.getStatus().name(), trainer.userId()));

        if (submission.getStudentUserId() != null) {
            boolean returned = submission.getStatus() == SubmissionStatus.RETURNED;
            events.notifyUsers(List.of(submission.getStudentUserId()), "ASSIGNMENT_RESULT",
                    returned ? "Work returned: " + assignment.getTitle() : "Marked: " + assignment.getTitle(),
                    returned ? "Your trainer has returned this assignment for rework."
                            : "You scored %d out of %d.".formatted(submission.getMarks(), assignment.getMaxMarks()),
                    "/student/assignments/" + assignment.getId());
        }

        // Doc S6.10/S14: marks are an academic record, so changing one is audited.
        events.audit(SERVICE_NAME, "SUBMISSION_EVALUATED", "Submission", submission.getId(), before,
                Map.of("status", submission.getStatus().name(),
                        "marks", String.valueOf(submission.getMarks())));
        return SubmissionResponse.from(submission);
    }

    @Override
    @Transactional(readOnly = true)
    public SubmissionResponse getSubmission(Long submissionId) {
        Submission submission = requireSubmission(submissionId);
        AppPrincipal caller = SecurityUtils.requirePrincipal();
        if (caller.isStudent()) {
            if (!submission.getStudentId().equals(caller.profileId())) {
                throw new ForbiddenOperationException("You may only view your own submissions.");
            }
        } else {
            access.requireManagesBatch(requireAssignment(submission.getAssignmentId()).getBatchId());
        }
        return SubmissionResponse.from(submission);
    }

    // -----------------------------------------------------------------

    private void announce(Assignment assignment) {
        events.publishAfterCommit(KafkaTopics.ASSIGNMENT_CREATED, new AssignmentCreatedEvent(
                DomainEvent.newId(), Instant.now(), assignment.getId(), assignment.getBatchId(),
                assignment.getTitle(), assignment.getDueAt(), assignment.getMaxMarks(),
                SecurityUtils.currentUserId()));
        events.notifyBatch(assignment.getBatchId(), "ASSIGNMENT",
                "New assignment: " + assignment.getTitle(),
                "Due " + assignment.getDueAt() + ". Out of " + assignment.getMaxMarks() + " marks.",
                "/student/assignments/" + assignment.getId());
    }

    /** Batch id to course id, from the caller's own batch list, when it is there. */
    private Long courseOfBatch(Long batchId) {
        return access.myBatches().stream()
                .filter(b -> batchId.equals(b.id()))
                .map(BatchClient.BatchSummary::courseId)
                .findFirst().orElse(null);
    }

    /** assignmentId -> {submitted, evaluated}, in one query for the whole list. */
    private Map<Long, long[]> countsFor(List<Long> assignmentIds) {
        if (assignmentIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, long[]> counts = new HashMap<>();
        for (Object[] row : submissionRepository.countsByAssignment(assignmentIds)) {
            counts.put((Long) row[0], new long[]{((Number) row[1]).longValue(), ((Number) row[2]).longValue()});
        }
        return counts;
    }

    private Assignment requireAssignment(Long id) {
        return assignmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment", id));
    }

    private Submission requireSubmission(Long id) {
        return submissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Submission", id));
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
