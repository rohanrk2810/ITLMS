package com.itilms.admission.service.impl;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.admission.client.IdentityClient;
import com.itilms.admission.dto.request.CreateTrainerRequest;
import com.itilms.admission.dto.request.UpdateTrainerRequest;
import com.itilms.admission.dto.response.TrainerResponse;
import com.itilms.admission.dto.response.TrainerSummaryResponse;
import com.itilms.admission.entity.Trainer;
import com.itilms.admission.entity.TrainerCourse;
import com.itilms.admission.entity.TrainerStatus;
import com.itilms.admission.repository.TrainerCourseRepository;
import com.itilms.admission.repository.TrainerRepository;
import com.itilms.admission.service.TrainerService;
import com.itilms.admission.specification.TrainerSpecifications;
import com.itilms.common.dto.PageResponse;
import com.itilms.common.event.EventPublisher;
import com.itilms.common.event.KafkaTopics;
import com.itilms.common.event.ProfileLinkedEvent;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.common.util.Codes;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrainerServiceImpl implements TrainerService {

    private static final String SERVICE_NAME = "admission-service";
    private static final int CODE_RETRY_LIMIT = 5;

    private final TrainerRepository trainerRepository;
    private final TrainerCourseRepository trainerCourseRepository;
    private final IdentityClient identityClient;
    private final EventPublisher events;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TrainerSummaryResponse> search(String status, String query, Pageable pageable) {
        Specification<Trainer> spec = Specification.allOf(
                TrainerSpecifications.hasStatus(status),
                TrainerSpecifications.matches(query));
        return PageResponse.from(trainerRepository.findAll(spec, pageable), TrainerSummaryResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public TrainerResponse get(Long id) {
        Trainer trainer = trainerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trainer", id));
        return TrainerResponse.from(trainer, trainerCourseRepository.findCourseIdsByTrainerId(id));
    }

    @Override
    @Transactional(readOnly = true)
    public TrainerResponse getByUserId(Long userId) {
        Trainer trainer = trainerRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No trainer profile is linked to this account"));
        return TrainerResponse.from(trainer,
                trainerCourseRepository.findCourseIdsByTrainerId(trainer.getId()));
    }

    @Override
    @Transactional
    public TrainerResponse create(CreateTrainerRequest request) {
        String email = request.email().trim().toLowerCase();

        var account = identityClient.createUser(IdentityClient.CreateUserPayload.withGeneratedPassword(
                request.firstName().trim(), request.lastName().trim(), email,
                request.phone().trim(), "TRAINER"));

        if (trainerRepository.existsByUserId(account.id())) {
            throw new BusinessRuleException("A trainer profile already exists for this account");
        }

        Trainer trainer = persistWithGeneratedCode(request, account.id(),
                request.firstName().trim() + " " + request.lastName().trim(), email);

        replaceCourseAssignments(trainer.getId(), request.courseIds());

        log.info("Created trainer {} ({}) on account {}",
                trainer.getId(), trainer.getEmployeeCode(), account.id());

        events.publishAfterCommit(KafkaTopics.PROFILE_LINKED,
                ProfileLinkedEvent.of(account.id(), "TRAINER", trainer.getId(), trainer.getEmployeeCode()));

        events.audit(SERVICE_NAME, "TRAINER_CREATED", "Trainer", trainer.getId(), null,
                Map.of("employeeCode", trainer.getEmployeeCode(), "email", email));

        return TrainerResponse.from(trainer,
                trainerCourseRepository.findCourseIdsByTrainerId(trainer.getId()));
    }

    @Override
    @Transactional
    public TrainerResponse update(Long id, UpdateTrainerRequest request) {
        Trainer trainer = trainerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trainer", id));

        TrainerStatus target = parseStatus(request.status());
        TrainerStatus previous = trainer.getStatus();

        trainer.setSpecialization(trim(request.specialization()));
        trainer.setQualification(trim(request.qualification()));
        trainer.setExperienceYears(request.experienceYears() == null
                ? BigDecimal.ZERO : request.experienceYears());
        trainer.setBio(trim(request.bio()));
        trainer.setJoinedOn(request.joinedOn());
        trainer.setStatus(target);
        trainerRepository.save(trainer);

        if (request.courseIds() != null) {
            replaceCourseAssignments(id, request.courseIds());
        }

        if (previous != target) {
            events.audit(SERVICE_NAME, "TRAINER_STATUS_CHANGED", "Trainer", id,
                    Map.of("status", previous.name()), Map.of("status", target.name()));
            log.info("Trainer {} status {} -> {}", id, previous, target);
        }

        return TrainerResponse.from(trainer, trainerCourseRepository.findCourseIdsByTrainerId(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrainerSummaryResponse> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return trainerRepository.findByIdIn(ids).stream().map(TrainerSummaryResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrainerSummaryResponse> findAvailableForCourse(Long courseId) {
        return trainerRepository.findAvailableForCourse(courseId).stream()
                .map(TrainerSummaryResponse::from).toList();
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    /**
     * Replaces the trainer's course list wholesale.
     *
     * <p>Delete-then-insert rather than diffing. The list is a handful of rows,
     * the write happens when someone edits a profile, and "the new list is
     * exactly what was submitted" is a rule with no edge cases — whereas a diff
     * has to decide what an omitted entry means.
     */
    private void replaceCourseAssignments(Long trainerId, List<Long> courseIds) {
        trainerCourseRepository.deleteByTrainerId(trainerId);
        if (courseIds == null || courseIds.isEmpty()) {
            return;
        }
        List<TrainerCourse> assignments = courseIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .map(courseId -> TrainerCourse.builder()
                        .trainerId(trainerId)
                        .courseId(courseId)
                        .build())
                .toList();
        trainerCourseRepository.saveAll(assignments);
    }

    private Trainer persistWithGeneratedCode(CreateTrainerRequest request, Long userId,
                                             String fullName, String email) {
        for (int attempt = 1; attempt <= CODE_RETRY_LIMIT; attempt++) {
            String code = Codes.trainerCode(trainerRepository.nextCodeSequence() + attempt - 1);
            try {
                return trainerRepository.saveAndFlush(Trainer.builder()
                        .userId(userId)
                        .employeeCode(code)
                        .fullName(fullName)
                        .email(email)
                        .phone(request.phone().trim())
                        .specialization(trim(request.specialization()))
                        .qualification(trim(request.qualification()))
                        .experienceYears(request.experienceYears() == null
                                ? BigDecimal.ZERO : request.experienceYears())
                        .bio(trim(request.bio()))
                        .joinedOn(request.joinedOn())
                        .status(TrainerStatus.ACTIVE)
                        .build());
            } catch (DataIntegrityViolationException collision) {
                if (attempt == CODE_RETRY_LIMIT) {
                    throw collision;
                }
                log.debug("Employee code {} was taken; retrying (attempt {})", code, attempt + 1);
            }
        }
        throw new IllegalStateException("Unreachable: the retry loop always returns or rethrows");
    }

    private TrainerStatus parseStatus(String value) {
        try {
            return TrainerStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new BusinessRuleException("Status must be ACTIVE, INACTIVE or ON_LEAVE");
        }
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
