package com.itilms.assessment.service;

import java.util.List;

import com.itilms.assessment.dto.request.SubmitAttemptRequest;
import com.itilms.assessment.dto.request.ViolationRequest;
import com.itilms.assessment.dto.response.AnswerSaveResponse;
import com.itilms.assessment.dto.response.AttemptResultResponse;
import com.itilms.assessment.dto.response.AttemptViewResponse;
import com.itilms.assessment.dto.response.CodingRunResponse;
import com.itilms.assessment.dto.response.ViolationResponse;

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

    /**
     * Runs a coding question's test cases against the student's code, keeps the code as their answer and
     * remembers the verdict. Nothing is marked yet: the marks are taken from this verdict when the attempt ends.
     */
    CodingRunResponse runTests(Long attemptId, Long questionId, String sourceCode);

    /**
     * A secure test's browser reports something (left the window, tried to copy...). The server decides what
     * it counts for: a counted violation below the limit warns, the one that reaches it ends the attempt.
     * On a test that is not secure, or an attempt already over, the report is accepted and changes nothing.
     */
    ViolationResponse.Outcome recordViolation(Long attemptId, ViolationRequest request);

    /** Everything the browser reported for one attempt, oldest first - for the trainer's review. */
    List<ViolationResponse.Entry> violations(Long attemptId);

    AttemptResultResponse submit(Long attemptId, SubmitAttemptRequest request);

    AttemptResultResponse result(Long attemptId);

    /** The signed-in student's attempts at one test. */
    List<AttemptResultResponse> myAttempts(Long quizId);

    /** Every finished attempt at a test - the trainer's results sheet. */
    List<AttemptResultResponse> quizResults(Long quizId);
}
