package com.itilms.batch.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.batch.client.AdmissionClient;
import com.itilms.batch.client.CourseClient;
import com.itilms.batch.dto.request.BatchCourseRequests;
import com.itilms.batch.dto.response.BatchCourseResponse;
import com.itilms.batch.dto.response.PublicBatchResponse;
import com.itilms.batch.entity.Batch;
import com.itilms.batch.entity.BatchCourse;
import com.itilms.batch.entity.BatchStatus;
import com.itilms.batch.entity.Enrollment;
import com.itilms.batch.entity.EnrollmentStatus;
import com.itilms.batch.repository.BatchCourseRepository;
import com.itilms.batch.repository.BatchRepository;
import com.itilms.batch.repository.EnrollmentRepository;
import com.itilms.common.event.DomainEvent;
import com.itilms.common.event.EnrollmentCreatedEvent;
import com.itilms.common.event.EventPublisher;
import com.itilms.common.event.KafkaTopics;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The courses a batch holds, and the students who have them because of it.
 *
 * <p>A batch is created for one course ({@code batches.course_id}) and can then hold more, each with its own trainer.
 * Everyone placed in the <em>whole</em> batch has all of them: the placement is one seat and carries the batch fee, and
 * every other course is an {@code included} enrolment next to it, which course-service turns into lessons and
 * progress and which finance ignores. A student placed in one course alone has that course only and pays its own fee.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchCourseService {

    private static final String SERVICE_NAME = "batch-service";

    private final BatchRepository batches;
    private final BatchCourseRepository batchCourses;
    private final EnrollmentRepository enrollments;
    private final CourseClient courseClient;
    private final AdmissionClient admissionClient;
    private final EventPublisher events;

    // -----------------------------------------------------------------
    // Read
    // -----------------------------------------------------------------

    /** The batch's own course first, then the ones added, oldest first. */
    @Transactional(readOnly = true)
    public List<BatchCourseResponse> courses(Batch batch) {
        List<BatchCourseResponse> all = new ArrayList<>();
        all.add(new BatchCourseResponse(batch.getCourseId(), batch.getCourseCode(), batch.getCourseTitle(),
                batch.getTrainerId(), batch.getTrainerName(), true));
        for (BatchCourse added : batchCourses.findByBatchIdOrderByAddedAt(batch.getId())) {
            all.add(new BatchCourseResponse(added.getCourseId(), added.getCourseCode(), added.getCourseTitle(),
                    added.getTrainerId(), added.getTrainerName(), false));
        }
        return all;
    }

    /** Whether the course is one the batch holds (its own or an added one). */
    @Transactional(readOnly = true)
    public boolean holds(Batch batch, Long courseId) {
        return batch.getCourseId().equals(courseId) || batchCourses.existsByBatchIdAndCourseId(batch.getId(), courseId);
    }

    /** Who teaches a course in a batch, and what it is called there. */
    public record Teaching(Long courseId, String courseTitle, Long trainerId, Long trainerUserId, String trainerName) {
    }

    /**
     * Resolves the course a lecture is for. Nothing, or the batch's own course, is the batch's own course (stored as
     * no course on the lecture); an added course brings its own trainer; anything else is refused.
     */
    @Transactional(readOnly = true)
    public Teaching teaching(Batch batch, Long courseId) {
        if (courseId == null || batch.getCourseId().equals(courseId)) {
            return new Teaching(null, batch.getCourseTitle(), batch.getTrainerId(), batch.getTrainerUserId(),
                    batch.getTrainerName());
        }
        BatchCourse added = batchCourses.findByBatchIdAndCourseId(batch.getId(), courseId)
                .orElseThrow(() -> new BusinessRuleException("That course is not part of this batch."));
        return new Teaching(added.getCourseId(), added.getCourseTitle(), added.getTrainerId(),
                added.getTrainerUserId(), added.getTrainerName());
    }

    /** The batches the website sells: a fee of their own, still taking students, and a free seat. */
    @Transactional(readOnly = true)
    public List<PublicBatchResponse> openForSale() {
        List<Batch> open = batches.findByStatusInAndFeeGreaterThanOrderByStartDateAsc(
                List.of(BatchStatus.PLANNED, BatchStatus.ONGOING), java.math.BigDecimal.ZERO);
        if (open.isEmpty()) {
            return List.of();
        }
        List<Long> ids = open.stream().map(Batch::getId).toList();
        java.util.Map<Long, Long> taken = new java.util.HashMap<>();
        enrollments.countActiveGroupedByBatch(ids).forEach(row -> taken.put((Long) row[0], (Long) row[1]));
        java.util.Map<Long, List<String>> added = new java.util.HashMap<>();
        batchCourses.findByBatchIdIn(ids).forEach(bc ->
                added.computeIfAbsent(bc.getBatchId(), k -> new ArrayList<>()).add(bc.getCourseTitle()));

        List<PublicBatchResponse> result = new ArrayList<>();
        for (Batch batch : open) {
            long seatsLeft = batch.getCapacity() - taken.getOrDefault(batch.getId(), 0L);
            if (seatsLeft <= 0) {
                continue;
            }
            List<String> titles = new ArrayList<>();
            titles.add(batch.getCourseTitle());
            titles.addAll(added.getOrDefault(batch.getId(), List.of()));
            result.add(new PublicBatchResponse(batch.getId(), batch.getBatchCode(), batch.getName(),
                    batch.getCourseId(), titles, batch.getStartDate(), batch.getEndDate(),
                    batch.getStartTime(), batch.getEndTime(), List.of(batch.getClassDays().split(",")),
                    batch.getMode().name(), seatsLeft, batch.getFee()));
        }
        return result;
    }

    // -----------------------------------------------------------------
    // Changing the courses
    // -----------------------------------------------------------------

    /** Puts a course into the batch and enrols everyone placed in the whole batch in it. */
    @Transactional
    public List<BatchCourseResponse> add(Long batchId, BatchCourseRequests.Add request) {
        Batch batch = require(batchId);
        Long courseId = request.courseId();
        if (holds(batch, courseId)) {
            throw new BusinessRuleException("The batch already holds that course.");
        }
        var detail = courseClient.get(courseId);
        if (detail == null || detail.course() == null) {
            throw new BusinessRuleException("That course could not be found, so it was not added.");
        }

        BatchCourse added = BatchCourse.builder()
                .batchId(batchId)
                .courseId(courseId)
                .courseCode(detail.course().code())
                .courseTitle(detail.course().title())
                .build();
        applyTrainer(added, request.trainerId());
        batchCourses.save(added);

        int enrolled = 0;
        for (Enrollment placement : enrollments.findWholeBatchPlacements(batchId, batch.getCourseId())) {
            openIncluded(batch, placement, added);
            enrolled++;
        }

        events.audit(SERVICE_NAME, "BATCH_COURSE_ADDED", "Batch", batchId, null,
                Map.of("courseId", courseId, "studentsEnrolled", enrolled));
        log.info("Added course {} to batch {}; {} student(s) enrolled in it", courseId, batchId, enrolled);
        return courses(batch);
    }

    @Transactional
    public List<BatchCourseResponse> changeTrainer(Long batchId, Long courseId,
                                                   BatchCourseRequests.ChangeTrainer request) {
        Batch batch = require(batchId);
        BatchCourse added = batchCourses.findByBatchIdAndCourseId(batchId, courseId)
                .orElseThrow(() -> new BusinessRuleException(
                        "That course is not an added course of this batch. The trainer of the batch's own course "
                                + "is changed by editing the batch."));
        applyTrainer(added, request.trainerId());
        batchCourses.save(added);
        events.audit(SERVICE_NAME, "BATCH_COURSE_TRAINER_CHANGED", "Batch", batchId, null,
                Map.of("courseId", courseId, "trainerId", String.valueOf(request.trainerId())));
        return courses(batch);
    }

    /** Takes an added course out of the batch; the students' lessons for it are closed (their progress is kept). */
    @Transactional
    public List<BatchCourseResponse> remove(Long batchId, Long courseId) {
        Batch batch = require(batchId);
        if (batch.getCourseId().equals(courseId)) {
            throw new BusinessRuleException("The course the batch was created for cannot be taken out.");
        }
        BatchCourse added = batchCourses.findByBatchIdAndCourseId(batchId, courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Batch course", courseId));

        int closed = 0;
        for (Enrollment included : enrollments.findByBatchIdAndCourseIdAndIncludedTrueAndStatus(
                batchId, courseId, EnrollmentStatus.ACTIVE)) {
            close(included, "The course was taken out of the batch");
            closed++;
        }
        batchCourses.delete(added);

        events.audit(SERVICE_NAME, "BATCH_COURSE_REMOVED", "Batch", batchId, null,
                Map.of("courseId", courseId, "studentsClosed", closed));
        return courses(batch);
    }

    // -----------------------------------------------------------------
    // Called when a student joins, leaves or moves
    // -----------------------------------------------------------------

    /** Gives a student placed in the whole batch every added course. */
    @Transactional
    public void openAllIncluded(Batch batch, Enrollment placement) {
        for (BatchCourse added : batchCourses.findByBatchIdOrderByAddedAt(batch.getId())) {
            openIncluded(batch, placement, added);
        }
    }

    /** Closes the courses that came with a student's placement (they left, or moved to another batch). */
    @Transactional
    public void closeAllIncluded(Enrollment placement, String reason) {
        for (Enrollment included : enrollments.findByStudentIdAndBatchIdAndIncludedTrueAndStatus(
                placement.getStudentId(), placement.getBatchId(), EnrollmentStatus.ACTIVE)) {
            close(included, reason);
        }
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private void openIncluded(Batch batch, Enrollment placement, BatchCourse course) {
        boolean already = !enrollments.findByStudentIdAndBatchIdAndIncludedTrueAndStatus(
                placement.getStudentId(), batch.getId(), EnrollmentStatus.ACTIVE).stream()
                .filter(e -> e.getCourseId().equals(course.getCourseId())).toList().isEmpty();
        if (already) {
            return;
        }
        Enrollment included = enrollments.save(Enrollment.builder()
                .studentId(placement.getStudentId())
                .userId(placement.getUserId())
                .studentCode(placement.getStudentCode())
                .studentName(placement.getStudentName())
                .courseId(course.getCourseId())
                .batchId(batch.getId())
                .included(true)
                .accessUntil(placement.getAccessUntil())
                .status(EnrollmentStatus.ACTIVE)
                .build());

        // course-service seeds the lessons and progress from this; finance sees feeIncluded and raises nothing.
        events.publishAfterCommit(KafkaTopics.ENROLLMENT_CREATED, new EnrollmentCreatedEvent(
                DomainEvent.newId(), Instant.now(),
                included.getId(), included.getStudentId(), included.getUserId(),
                included.getCourseId(), batch.getId(), batch.getBatchCode(),
                included.getStudentCode(), included.getStudentName(), true, null));
    }

    private void close(Enrollment included, String reason) {
        included.drop(reason);
        enrollments.save(included);
        events.publishAfterCommit(KafkaTopics.ENROLLMENT_CLOSED, new EnrollmentCreatedEvent(
                DomainEvent.newId(), Instant.now(),
                included.getId(), included.getStudentId(), included.getUserId(),
                included.getCourseId(), included.getBatchId(), null));
    }

    private void applyTrainer(BatchCourse course, Long trainerId) {
        if (trainerId == null) {
            course.setTrainerId(null);
            course.setTrainerUserId(null);
            course.setTrainerName(null);
            return;
        }
        var trainers = admissionClient.lookupTrainers(List.of(trainerId));
        if (trainers.isEmpty()) {
            throw new BusinessRuleException("That trainer could not be found.");
        }
        course.setTrainerId(trainerId);
        course.setTrainerUserId(trainers.get(0).userId());
        course.setTrainerName(trainers.get(0).fullName());
    }

    private Batch require(Long batchId) {
        return batches.findById(batchId).orElseThrow(() -> new ResourceNotFoundException("Batch", batchId));
    }
}
