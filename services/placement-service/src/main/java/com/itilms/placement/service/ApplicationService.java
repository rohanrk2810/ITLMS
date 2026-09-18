package com.itilms.placement.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.common.event.ApplicationStageChangedEvent;
import com.itilms.common.event.DomainEvent;
import com.itilms.common.event.EventPublisher;
import com.itilms.common.event.KafkaTopics;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.DuplicateResourceException;
import com.itilms.common.exception.ForbiddenOperationException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.common.security.AppPrincipal;
import com.itilms.common.security.SecurityUtils;
import com.itilms.placement.config.PlacementProperties;
import com.itilms.placement.dto.request.ApplyRequest;
import com.itilms.placement.dto.request.StageChangeRequest;
import com.itilms.placement.dto.response.ApplicationResponse;
import com.itilms.placement.dto.response.PlacementDashboardResponse;
import com.itilms.placement.dto.response.StageChangeResponse;
import com.itilms.placement.entity.ApplicationStage;
import com.itilms.placement.entity.Company;
import com.itilms.placement.entity.JobApplication;
import com.itilms.placement.entity.JobOpening;
import com.itilms.placement.entity.JobStatus;
import com.itilms.placement.entity.StageChange;
import com.itilms.placement.repository.CompanyRepository;
import com.itilms.placement.repository.JobApplicationRepository;
import com.itilms.placement.repository.JobOpeningRepository;
import com.itilms.placement.repository.StageChangeRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Applications and the interview pipeline (Doc S6.14, S7.4). */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationService {

    private static final String SERVICE_NAME = "placement-service";

    private final JobApplicationRepository applicationRepository;
    private final JobOpeningRepository jobRepository;
    private final CompanyRepository companyRepository;
    private final StageChangeRepository historyRepository;
    private final EligibilityChecker eligibility;
    private final PlacementProperties props;
    private final EventPublisher events;

    /** Doc S11: POST /api/jobs/{id}/apply. The eligibility rules are enforced here, not just shown. */
    @Transactional
    public ApplicationResponse apply(Long jobId, ApplyRequest request) {
        AppPrincipal student = SecurityUtils.requirePrincipal();
        if (student.studentIdOrNull() == null) {
            throw new ForbiddenOperationException("Your account is not linked to a student profile yet.");
        }
        JobOpening job = jobRepository.findById(jobId).orElseThrow(() -> new ResourceNotFoundException("Job", jobId));
        if (job.getStatus() == JobStatus.DRAFT) {
            throw new ResourceNotFoundException("Job", jobId);
        }
        if (!job.acceptsApplicationsOn(LocalDate.now(props.getZone()))) {
            throw new BusinessRuleException("This job is no longer accepting applications.");
        }
        if (applicationRepository.existsByJobIdAndStudentId(jobId, student.profileId())) {
            throw new DuplicateResourceException("You have already applied for this job.");
        }

        EligibilityChecker.Result check = eligibility.check(job, eligibility.snapshot(student.profileId()));
        if (!check.eligible()) {
            throw new BusinessRuleException("NOT_ELIGIBLE", String.join(" ", check.reasons()));
        }

        JobApplication application = applicationRepository.save(JobApplication.builder()
                .jobId(jobId)
                .studentId(student.profileId())
                .studentUserId(student.userId())
                .studentName(student.fullName())
                .courseId(check.courseId())
                .resumeRef(trim(request.resumeRef()))
                .coverNote(trim(request.coverNote()))
                .appliedAt(Instant.now())
                .stage(ApplicationStage.APPLIED)
                .build());
        record(application, null, ApplicationStage.APPLIED, null, "Applied", student.userId());

        String company = companyName(job.getCompanyId());
        log.info("Student {} applied for job {}", student.profileId(), jobId);
        return ApplicationResponse.of(application, job.getTitle(), company);
    }

    /** Moves an application along the pipeline. Placement staff only; every move is recorded. */
    @Transactional
    public ApplicationResponse changeStage(Long applicationId, StageChangeRequest request) {
        AppPrincipal staff = SecurityUtils.requirePrincipal();
        JobApplication application = require(applicationId);
        ApplicationStage next = parseStage(request.stage());
        if (next == ApplicationStage.WITHDRAWN || next == ApplicationStage.APPLIED) {
            throw new BusinessRuleException("Only the student can withdraw an application.");
        }
        return move(application, next, request, staff);
    }

    /** The student pulls out. Allowed until a decision is made. */
    @Transactional
    public ApplicationResponse withdraw(Long applicationId) {
        AppPrincipal student = SecurityUtils.requirePrincipal();
        JobApplication application = require(applicationId);
        if (!application.getStudentId().equals(student.profileId())) {
            throw new ResourceNotFoundException("Application", applicationId);
        }
        return move(application, ApplicationStage.WITHDRAWN,
                new StageChangeRequest("WITHDRAWN", null, null, null, "Withdrawn by the student"), student);
    }

    private ApplicationResponse move(JobApplication application, ApplicationStage next,
                                     StageChangeRequest request, AppPrincipal actor) {
        ApplicationStage previous = application.getStage();
        if (!previous.canMoveTo(next)) {
            throw new BusinessRuleException("An application cannot move from %s to %s.".formatted(previous, next));
        }

        Integer round = null;
        if (next == ApplicationStage.INTERVIEW) {
            round = request.roundNo() != null ? request.roundNo()
                    : (application.getCurrentRound() == null ? 1 : application.getCurrentRound() + 1);
            application.setCurrentRound(round);
            application.setNextInterviewAt(request.nextInterviewAt());
        } else {
            application.setNextInterviewAt(null);
        }
        if (next == ApplicationStage.SELECTED) {
            application.setOfferDetails(trim(request.offerDetails()));
        }
        if (next.isFinal()) {
            application.setDecidedAt(Instant.now());
        }
        application.setStage(next);
        applicationRepository.save(application);
        record(application, previous, next, round, trim(request.note()), actor.userId());

        JobOpening job = jobRepository.findById(application.getJobId()).orElseThrow();
        String company = companyName(job.getCompanyId());

        events.publishAfterCommit(KafkaTopics.APPLICATION_STAGE_CHANGED, String.valueOf(application.getStudentId()),
                new ApplicationStageChangedEvent(DomainEvent.newId(), Instant.now(), application.getId(),
                        job.getId(), job.getTitle(), company, application.getStudentId(),
                        application.getStudentUserId(), previous.name(), next.name(),
                        next.isFinal() ? next.name() : null, actor.userId()));
        if (application.getStudentUserId() != null && !actor.isStudent()) {
            events.notifyUsers(List.of(application.getStudentUserId()), "PLACEMENT",
                    "%s - %s".formatted(company, job.getTitle()), describe(next, round), "/student/placements");
        }
        // Doc S14: placement status changes are auditable.
        events.audit(SERVICE_NAME, "APPLICATION_STAGE_CHANGED", "JobApplication", application.getId(),
                Map.of("stage", previous.name()), Map.of("stage", next.name()));
        return ApplicationResponse.of(application, job.getTitle(), company);
    }

    // -----------------------------------------------------------------
    // Reading
    // -----------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ApplicationResponse> mine() {
        AppPrincipal student = SecurityUtils.requirePrincipal();
        if (student.studentIdOrNull() == null) {
            throw new ForbiddenOperationException("Your account is not linked to a student profile yet.");
        }
        return describeAll(applicationRepository.findByStudentIdOrderByAppliedAtDesc(student.profileId()));
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> forJob(Long jobId) {
        return describeAll(applicationRepository.findByJobIdOrderByAppliedAtAsc(jobId));
    }

    @Transactional(readOnly = true)
    public List<StageChangeResponse> history(Long applicationId) {
        JobApplication application = require(applicationId);
        SecurityUtils.requireStudentOwnershipOrStaff(application.getStudentId());
        return historyRepository.findByApplicationIdOrderByChangedAtAsc(applicationId).stream()
                .map(StageChangeResponse::from).toList();
    }

    /** Doc S7.4 "Placement Record Updated": every selection, most recent first. */
    @Transactional(readOnly = true)
    public List<ApplicationResponse> placements() {
        return describeAll(applicationRepository.findByStageOrderByDecidedAtDesc(ApplicationStage.SELECTED));
    }

    /** Doc S15 placement dashboard. */
    @Transactional(readOnly = true)
    public PlacementDashboardResponse dashboard() {
        Map<String, Long> byStage = new LinkedHashMap<>();
        for (ApplicationStage stage : ApplicationStage.values()) {
            byStage.put(stage.name(), 0L);
        }
        long total = 0;
        for (Object[] row : applicationRepository.countByStage()) {
            long count = ((Number) row[1]).longValue();
            byStage.put(row[0].toString(), count);
            total += count;
        }

        List<JobApplication> selected = applicationRepository.findByStageOrderByDecidedAtDesc(ApplicationStage.SELECTED);
        Map<Long, JobOpening> jobs = jobRepository.findAllById(selected.stream().map(JobApplication::getJobId).distinct().toList())
                .stream().collect(Collectors.toMap(JobOpening::getId, Function.identity()));
        Map<Long, Long> perCompany = new HashMap<>();
        selected.forEach(a -> {
            JobOpening job = jobs.get(a.getJobId());
            if (job != null) {
                perCompany.merge(job.getCompanyId(), 1L, Long::sum);
            }
        });
        Map<Long, String> names = companyRepository.findAllById(perCompany.keySet()).stream()
                .collect(Collectors.toMap(Company::getId, Company::getName));
        List<PlacementDashboardResponse.CompanyPlacements> byCompany = perCompany.entrySet().stream()
                .map(e -> new PlacementDashboardResponse.CompanyPlacements(e.getKey(), names.get(e.getKey()), e.getValue()))
                .sorted(Comparator.comparingLong(PlacementDashboardResponse.CompanyPlacements::selected).reversed())
                .toList();

        return new PlacementDashboardResponse(jobRepository.countByStatus(JobStatus.OPEN), total, byStage,
                byStage.get(ApplicationStage.SELECTED.name()), byCompany);
    }

    // -----------------------------------------------------------------

    private void record(JobApplication application, ApplicationStage from, ApplicationStage to,
                        Integer round, String note, Long by) {
        historyRepository.save(StageChange.builder().applicationId(application.getId())
                .fromStage(from).toStage(to).roundNo(round).note(note).changedBy(by).changedAt(Instant.now()).build());
    }

    private List<ApplicationResponse> describeAll(List<JobApplication> applications) {
        if (applications.isEmpty()) {
            return List.of();
        }
        Map<Long, JobOpening> jobs = jobRepository.findAllById(
                        applications.stream().map(JobApplication::getJobId).distinct().toList())
                .stream().collect(Collectors.toMap(JobOpening::getId, Function.identity()));
        Map<Long, String> companies = companyRepository.findAllById(
                        jobs.values().stream().map(JobOpening::getCompanyId).distinct().toList())
                .stream().collect(Collectors.toMap(Company::getId, Company::getName));
        return applications.stream().map(a -> {
            JobOpening job = jobs.get(a.getJobId());
            return ApplicationResponse.of(a, job == null ? null : job.getTitle(),
                    job == null ? null : companies.get(job.getCompanyId()));
        }).toList();
    }

    private static String describe(ApplicationStage stage, Integer round) {
        return switch (stage) {
            case SHORTLISTED -> "You have been shortlisted.";
            case INTERVIEW -> "You are invited to interview round %d.".formatted(round);
            case ON_HOLD -> "Your application is on hold.";
            case SELECTED -> "Congratulations - you have been selected.";
            case REJECTED -> "The company has decided not to go ahead with your application.";
            default -> "Your application was updated.";
        };
    }

    private String companyName(Long companyId) {
        return companyRepository.findById(companyId).map(Company::getName).orElse(null);
    }

    private JobApplication require(Long id) {
        return applicationRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Application", id));
    }

    private static ApplicationStage parseStage(String value) {
        try {
            return ApplicationStage.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("Unknown stage: " + value);
        }
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
