package com.itilms.assessment.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.itilms.assessment.client.BatchClient;
import com.itilms.assessment.entity.Quiz;
import com.itilms.common.exception.ForbiddenOperationException;
import com.itilms.common.security.AppPrincipal;
import com.itilms.common.security.SecurityUtils;

import lombok.RequiredArgsConstructor;

/**
 * Who may set, sit and mark work - decided in one place.
 *
 * <p>Doc S14: "Only the appropriate trainer/staff can see or evaluate
 * submissions belonging to their batches." A role check alone cannot express
 * that - every trainer holds the TRAINER role - so these checks ask
 * batch-service which batches the caller actually teaches or attends.
 *
 * <p>Staff (admin, coordinator) pass every check here. Their oversight is the
 * point of the role.
 */
@Component
@RequiredArgsConstructor
public class AssessmentAccess {

    private final BatchClient batchClient;

    /** The caller must be staff, or a trainer of this batch. */
    public AppPrincipal requireManagesBatch(Long batchId) {
        AppPrincipal caller = SecurityUtils.requirePrincipal();
        if (caller.isStaff()) {
            return caller;
        }
        if (caller.isTrainer() && teaches(batchId)) {
            return caller;
        }
        throw new ForbiddenOperationException("This batch is not one you teach.");
    }

    /**
     * The caller must be staff, or teach the test's batch - or, for a test set
     * for the whole course, teach at least one batch of that course.
     */
    public AppPrincipal requireManagesQuiz(Quiz quiz) {
        return requireManagesCourseOrBatch(quiz.getCourseId(), quiz.getBatchId());
    }

    public AppPrincipal requireManagesCourseOrBatch(Long courseId, Long batchId) {
        if (batchId != null) {
            return requireManagesBatch(batchId);
        }
        AppPrincipal caller = SecurityUtils.requirePrincipal();
        if (caller.isStaff()) {
            return caller;
        }
        if (caller.isTrainer() && batchClient.myBatches().stream()
                .anyMatch(b -> courseId.equals(b.courseId()))) {
            return caller;
        }
        throw new ForbiddenOperationException("You do not teach this course.");
    }

    /** The signed-in student, with a linked profile - or a refusal. */
    public AppPrincipal requireStudent() {
        AppPrincipal caller = SecurityUtils.requirePrincipal();
        if (!caller.isStudent() || caller.profileId() == null) {
            throw new ForbiddenOperationException("Only a student with a linked profile can do this.");
        }
        return caller;
    }

    /** The student must hold an active place in the batch, checked now rather than cached. */
    public void requireEnrolled(Long batchId, Long studentId) {
        var check = batchClient.isEnrolled(batchId, studentId);
        if (check == null || !check.enrolled()) {
            throw new ForbiddenOperationException("You are not enrolled in this batch.");
        }
    }

    /**
     * Which of the student's batches a test is being taken through.
     *
     * <p>A batch-specific test needs that exact batch; a course-wide test is
     * open to any batch of the course. The answer is recorded on the attempt,
     * so results can later be grouped by batch.
     */
    public Optional<Long> studentBatchFor(Quiz quiz) {
        return myBatches().stream()
                .filter(b -> quiz.getBatchId() != null
                        ? quiz.getBatchId().equals(b.id())
                        : quiz.getCourseId().equals(b.courseId()))
                .map(BatchClient.BatchSummary::id)
                .findFirst();
    }

    /** The caller's own batches, as batch-service scopes them by token. */
    public List<BatchClient.BatchSummary> myBatches() {
        return batchClient.myBatches();
    }

    private boolean teaches(Long batchId) {
        return batchClient.myBatches().stream().anyMatch(b -> batchId.equals(b.id()));
    }
}
