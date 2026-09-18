package com.itilms.assessment.service;

import java.util.List;

import com.itilms.assessment.dto.request.SubmitAttemptRequest;
import com.itilms.assessment.dto.response.AnswerSaveResponse;
import com.itilms.assessment.dto.response.AttemptResultResponse;
import com.itilms.assessment.dto.response.AttemptViewResponse;

/** Sitting a test (Doc S6.11, S11). */
public interface AttemptService {

    /**
     * Starts a sitting, or resumes the one already running.
     *
     * <p>Resuming rather than refusing matters: a student whose browser crashed
     * must get their paper back with their saved answers and the same deadline,
     * not lose an attempt.
     */
    AttemptViewResponse start(Long quizId);

    /** The paper for a sitting that is still running. */
    AttemptViewResponse paper(Long attemptId);

    /**
     * Saves answers part-way through.
     *
     * <p>What the student has saved is what gets scored if their time runs out,
     * so the page saves as they go rather than only at the end.
     */
    AnswerSaveResponse saveAnswers(Long attemptId, SubmitAttemptRequest request);

    AttemptResultResponse submit(Long attemptId, SubmitAttemptRequest request);

    AttemptResultResponse result(Long attemptId);

    /** The signed-in student's attempts at one test. */
    List<AttemptResultResponse> myAttempts(Long quizId);

    /** Every finished attempt at a test - the trainer's results sheet. */
    List<AttemptResultResponse> quizResults(Long quizId);
}
