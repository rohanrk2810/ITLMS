package com.itilms.batch.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Pageable;

import com.itilms.batch.dto.request.CreateBatchRequest;
import com.itilms.batch.dto.request.EnrollStudentRequest;
import com.itilms.batch.dto.request.UpdateBatchRequest;
import com.itilms.batch.dto.response.BatchResponse;
import com.itilms.batch.dto.response.BatchSummaryResponse;
import com.itilms.batch.dto.response.EnrollmentResponse;
import com.itilms.batch.dto.response.EnrollmentResultResponse;
import com.itilms.batch.dto.response.StudentEnrollmentResponse;
import com.itilms.common.dto.PageResponse;

/** Batches and who is in them (Doc S6.6). */
public interface BatchService {

    PageResponse<BatchSummaryResponse> search(String status, Long courseId, Long trainerId,
                                              String mode, String query, Pageable pageable);

    BatchResponse get(Long batchId);

    BatchResponse create(CreateBatchRequest request);

    BatchResponse update(Long batchId, UpdateBatchRequest request);

    /**
     * Enrols students, checking capacity and the no-duplicate rule per student.
     *
     * <p>Partial success is the normal outcome and is reported, not treated as
     * failure: one already-enrolled student should not block the other nineteen.
     */
    EnrollmentResultResponse enroll(Long batchId, EnrollStudentRequest request);

    /** The active register for a batch. */
    List<EnrollmentResponse> roster(Long batchId);

    EnrollmentResponse dropStudent(Long enrollmentId, String reason);

    /** Moves a student to another batch of the same course. */
    EnrollmentResponse transferStudent(Long enrollmentId, Long targetBatchId);

    /** Batches the signed-in student is currently in. */
    List<BatchSummaryResponse> myBatches(Long studentId);

    /** Batches a trainer teaches, primary or co-trainer. */
    List<BatchSummaryResponse> trainerBatches(Long trainerId);

    List<BatchSummaryResponse> findByIds(Collection<Long> ids);

    Map<String, Long> counts();

    /** Batch ids a student may see — used to scope their timetable. */
    List<Long> activeBatchIdsForStudent(Long studentId);

    /**
     * Every enrolment a student has, current and past, with the batch's details, for their progress report.
     * Internal: it does not check who is asking; reporting-service decides that before it calls.
     */
    List<StudentEnrollmentResponse> enrollmentsOf(Long studentId);

    /** Batch ids a trainer may act on — the ownership guard for attendance. */
    List<Long> batchIdsForTrainer(Long trainerId);

    /**
     * Whether a student currently holds an active place in a batch.
     *
     * <p>Exists for liveclass-service, which has to answer "may this person
     * enter this room?" before it mints a join token and cannot read the
     * roster table itself. A single boolean rather than the roster, because
     * that is the whole question and handing over forty names to answer it
     * would leak the class list to whoever asked.
     */
    boolean isActivelyEnrolled(Long batchId, Long studentId);
}
