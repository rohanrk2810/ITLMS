package com.itilms.assessment.service;

import java.util.List;

import org.springframework.data.domain.Pageable;

import com.itilms.assessment.dto.request.CreateAssignmentRequest;
import com.itilms.assessment.dto.request.EvaluateSubmissionRequest;
import com.itilms.assessment.dto.request.SubmitAssignmentRequest;
import com.itilms.assessment.dto.response.AssignmentResponse;
import com.itilms.assessment.dto.response.SubmissionResponse;
import com.itilms.common.dto.PageResponse;

/** Assignments and submissions (Doc S6.10). */
public interface AssignmentService {

    AssignmentResponse create(CreateAssignmentRequest request);

    AssignmentResponse update(Long id, CreateAssignmentRequest request);

    AssignmentResponse publish(Long id);

    /** Stops new submissions. Marking carries on. */
    AssignmentResponse close(Long id);

    /** One assignment, shaped for whoever is asking. */
    AssignmentResponse get(Long id);

    /** A batch's assignments with submission counts - the trainer's view. */
    PageResponse<AssignmentResponse> forBatch(Long batchId, Pageable pageable);

    /** Published work in the signed-in student's batches, with their own submission. */
    List<AssignmentResponse> mine();

    /**
     * Hands work in, or replaces earlier work.
     *
     * <p>Lateness is decided here from the server's clock and the stored
     * deadline (Doc S14), never from anything the client sends.
     */
    SubmissionResponse submit(Long assignmentId, SubmitAssignmentRequest request);

    List<SubmissionResponse> submissions(Long assignmentId);

    /** A trainer's queue: unmarked work across every batch they teach, oldest first. */
    List<SubmissionResponse> awaitingEvaluation(Long batchId);

    SubmissionResponse evaluate(Long submissionId, EvaluateSubmissionRequest request);

    SubmissionResponse getSubmission(Long submissionId);
}
