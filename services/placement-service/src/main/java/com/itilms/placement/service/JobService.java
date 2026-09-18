package com.itilms.placement.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.common.dto.PageResponse;
import com.itilms.common.event.DomainEvent;
import com.itilms.common.event.EventPublisher;
import com.itilms.common.event.JobPostedEvent;
import com.itilms.common.event.KafkaTopics;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.ForbiddenOperationException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.common.security.AppPrincipal;
import com.itilms.common.security.SecurityUtils;
import com.itilms.placement.config.PlacementProperties;
import com.itilms.placement.dto.request.JobRequest;
import com.itilms.placement.dto.response.JobResponse;
import com.itilms.placement.entity.Company;
import com.itilms.placement.entity.JobApplication;
import com.itilms.placement.entity.JobOpening;
import com.itilms.placement.entity.JobStatus;
import com.itilms.placement.entity.JobType;
import com.itilms.placement.repository.CompanyRepository;
import com.itilms.placement.repository.JobApplicationRepository;
import com.itilms.placement.repository.JobOpeningRepository;

import lombok.RequiredArgsConstructor;

/** Job openings (Doc S6.14, S7.4). */
@Service
@RequiredArgsConstructor
public class JobService {

    private static final String SERVICE_NAME = "placement-service";

    private final JobOpeningRepository jobRepository;
    private final CompanyRepository companyRepository;
    private final JobApplicationRepository applicationRepository;
    private final EligibilityChecker eligibility;
    private final PlacementProperties props;
    private final EventPublisher events;

    @Transactional
    public JobResponse create(JobRequest request) {
        Company company = requireActiveCompany(request.companyId());
        JobOpening job = new JobOpening();
        apply(job, request);
        job.setStatus(JobStatus.DRAFT);
        job = jobRepository.save(job);
        return JobResponse.forStaff(job, company.getName(), 0);
    }

    /**
     * Edits the opening. Eligibility rules cannot be tightened once students
     * have applied: a student who qualified yesterday must not find their
     * application standing against rules it never met.
     */
    @Transactional
    public JobResponse update(Long id, JobRequest request) {
        JobOpening job = require(id);
        if (job.getStatus() == JobStatus.CANCELLED) {
            throw new BusinessRuleException("A cancelled job cannot be edited.");
        }
        boolean hasApplicants = applicationRepository.countByJobId(id) > 0;
        if (hasApplicants && tightensRules(job, request)) {
            throw new BusinessRuleException(
                    "Students have already applied, so the eligibility rules can be relaxed but not tightened.");
        }
        Company company = requireActiveCompany(request.companyId());
        apply(job, request);
        job = jobRepository.save(job);
        return JobResponse.forStaff(job, company.getName(), applicationRepository.countByJobId(id));
    }

    /** Opens the job and tells eligible students (Doc S7.4 "Eligible Students Notified"). */
    @Transactional
    public JobResponse publish(Long id) {
        JobOpening job = require(id);
        if (job.getStatus() != JobStatus.DRAFT) {
            throw new BusinessRuleException("Only a draft job can be published.");
        }
        if (job.getApplicationDeadline() != null && job.getApplicationDeadline().isBefore(LocalDate.now(props.getZone()))) {
            throw new BusinessRuleException("The application deadline has already passed.");
        }
        Company company = requireActiveCompany(job.getCompanyId());
        job.publish(Instant.now());
        job = jobRepository.save(job);

        // notification-service expands the course list into the students to
        // tell; this service does not keep a roster.
        events.publishAfterCommit(KafkaTopics.JOB_POSTED, new JobPostedEvent(DomainEvent.newId(), Instant.now(),
                job.getId(), company.getId(), company.getName(), job.getTitle(), job.getPackageOffered(),
                job.getApplicationDeadline(), List.copyOf(job.getEligibleCourseIds()),
                job.getMinAttendancePercent(), job.getMinScorePercent()));
        events.audit(SERVICE_NAME, "JOB_PUBLISHED", "JobOpening", job.getId(), null,
                Map.of("company", company.getName(), "title", job.getTitle()));
        return JobResponse.forStaff(job, company.getName(), 0);
    }

    @Transactional
    public JobResponse close(Long id, boolean cancelled) {
        JobOpening job = require(id);
        if (job.getStatus() != JobStatus.OPEN && job.getStatus() != JobStatus.DRAFT) {
            throw new BusinessRuleException("This job is already closed.");
        }
        job.setStatus(cancelled ? JobStatus.CANCELLED : JobStatus.CLOSED);
        job = jobRepository.save(job);
        return JobResponse.forStaff(job, companyName(job.getCompanyId()),
                applicationRepository.countByJobId(id));
    }

    // -----------------------------------------------------------------
    // Reading
    // -----------------------------------------------------------------

    /** Doc S11: GET /api/jobs. Students see open jobs with their eligibility; staff see everything. */
    @Transactional(readOnly = true)
    public List<JobResponse> openJobsForStudent() {
        AppPrincipal student = requireStudent();
        List<JobOpening> jobs = jobRepository.findByStatusOrderByPublishedAtDesc(JobStatus.OPEN);
        if (jobs.isEmpty()) {
            return List.of();
        }
        Map<Long, String> companies = companyNames(jobs);
        Map<Long, JobApplication> mine = applicationRepository
                .findByStudentIdAndJobIdIn(student.profileId(), jobs.stream().map(JobOpening::getId).toList())
                .stream().collect(Collectors.toMap(JobApplication::getJobId, Function.identity()));

        EligibilityChecker.Snapshot snapshot = eligibility.snapshot(student.profileId());
        LocalDate today = LocalDate.now(props.getZone());
        List<JobResponse> result = new ArrayList<>();
        for (JobOpening job : jobs) {
            JobApplication application = mine.get(job.getId());
            EligibilityChecker.Result check = eligibility.check(job, snapshot);
            List<String> reasons = new ArrayList<>(check.reasons());
            if (!job.acceptsApplicationsOn(today)) {
                reasons.add("The application deadline has passed.");
            }
            result.add(JobResponse.forStudent(job, companies.get(job.getCompanyId()),
                    reasons.isEmpty(), reasons, application == null ? null : application.getStage().name()));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public PageResponse<JobResponse> list(String status, Long companyId, Pageable pageable) {
        Page<JobOpening> page;
        if (companyId != null) {
            page = jobRepository.findByCompanyIdOrderByCreatedAtDesc(companyId, pageable);
        } else if (status != null && !status.isBlank()) {
            page = jobRepository.findByStatusOrderByCreatedAtDesc(parseStatus(status), pageable);
        } else {
            page = jobRepository.findAllByOrderByCreatedAtDesc(pageable);
        }
        Map<Long, String> companies = companyNames(page.getContent());
        Map<Long, Long> counts = new java.util.HashMap<>();
        List<Long> ids = page.map(JobOpening::getId).getContent();
        if (!ids.isEmpty()) {
            for (Object[] row : applicationRepository.countsByJob(ids)) {
                counts.put((Long) row[0], ((Number) row[1]).longValue());
            }
        }
        return PageResponse.from(page, j -> JobResponse.forStaff(j, companies.get(j.getCompanyId()),
                counts.getOrDefault(j.getId(), 0L)));
    }

    @Transactional(readOnly = true)
    public JobResponse get(Long id) {
        JobOpening job = require(id);
        AppPrincipal caller = SecurityUtils.requirePrincipal();
        String company = companyName(job.getCompanyId());
        if (caller.isStudent()) {
            if (job.getStatus() == JobStatus.DRAFT) {
                throw new ResourceNotFoundException("Job", id);
            }
            EligibilityChecker.Result check = eligibility.check(job, eligibility.snapshot(caller.profileId()));
            String myStage = applicationRepository.findByJobIdAndStudentId(id, caller.profileId())
                    .map(a -> a.getStage().name()).orElse(null);
            return JobResponse.forStudent(job, company, check.eligible(), check.reasons(), myStage);
        }
        return JobResponse.forStaff(job, company, applicationRepository.countByJobId(id));
    }

    // -----------------------------------------------------------------

    JobOpening require(Long id) {
        return jobRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Job", id));
    }

    String companyName(Long companyId) {
        return companyRepository.findById(companyId).map(Company::getName).orElse(null);
    }

    private Map<Long, String> companyNames(List<JobOpening> jobs) {
        return companyRepository.findAllById(jobs.stream().map(JobOpening::getCompanyId).distinct().toList())
                .stream().collect(Collectors.toMap(Company::getId, Company::getName));
    }

    private Company requireActiveCompany(Long companyId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company", companyId));
        if (!company.isActive()) {
            throw new BusinessRuleException("This company is marked inactive.");
        }
        return company;
    }

    private static AppPrincipal requireStudent() {
        AppPrincipal caller = SecurityUtils.requirePrincipal();
        if (caller.studentIdOrNull() == null) {
            throw new ForbiddenOperationException("Your account is not linked to a student profile yet.");
        }
        return caller;
    }

    /** Whether the request narrows who may apply compared with the job as it stands. */
    static boolean tightensRules(JobOpening job, JobRequest request) {
        java.util.Set<Long> before = job.getEligibleCourseIds();
        java.util.Set<Long> after = request.eligibleCourseIds() == null ? java.util.Set.of() : request.eligibleCourseIds();
        boolean fewerCourses = !after.isEmpty() && (before.isEmpty() || !after.containsAll(before));
        boolean certificateAdded = Boolean.TRUE.equals(request.requireCertificate()) && !job.isRequireCertificate();
        boolean attendanceRaised = raised(job.getMinAttendancePercent(), request.minAttendancePercent());
        boolean scoreRaised = raised(job.getMinScorePercent(), request.minScorePercent());
        return fewerCourses || certificateAdded || attendanceRaised || scoreRaised;
    }

    private static boolean raised(Integer before, Integer after) {
        if (after == null) {
            return false;
        }
        return before == null ? after > 0 : after > before;
    }

    private static void apply(JobOpening job, JobRequest request) {
        job.setCompanyId(request.companyId());
        job.setTitle(request.title().trim());
        job.setDescription(request.description());
        job.setJobType(parseType(request.jobType()));
        job.setLocation(request.location());
        job.setPackageOffered(request.packageOffered());
        job.setOpenings(request.openings());
        job.setApplicationDeadline(request.applicationDeadline());
        job.setEligibleCourseIds(request.eligibleCourseIds() == null
                ? new LinkedHashSet<>() : new LinkedHashSet<>(request.eligibleCourseIds()));
        job.setRequireCertificate(Boolean.TRUE.equals(request.requireCertificate()));
        job.setMinAttendancePercent(request.minAttendancePercent());
        job.setMinScorePercent(request.minScorePercent());
        job.setEligibilityNotes(request.eligibilityNotes());
    }

    private static JobType parseType(String value) {
        if (value == null || value.isBlank()) {
            return JobType.FULL_TIME;
        }
        try {
            return JobType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("Job type must be FULL_TIME, INTERNSHIP, CONTRACT or PART_TIME.");
        }
    }

    private static JobStatus parseStatus(String value) {
        try {
            return JobStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("Status must be DRAFT, OPEN, CLOSED or CANCELLED.");
        }
    }
}
